package cn.cloudcat.user.message.chat;

import cn.cloudcat.user.session.service.UserConversationService;
import cn.cloudcat.user.common.model.WifiDeviceSnapshot;
import cn.cloudcat.user.common.spi.UserDeviceReader;
import cn.cloudcat.user.session.entity.UserConversation;
import cn.cloudcat.user.message.agent.human.HumanChatSessionManager;
import cn.cloudcat.user.message.agent.service.AgentTaskManager;
import cn.cloudcat.user.message.profile.ConsultationProfileService;
import cn.cloudcat.user.knowledge.service.UserActionTemplateService;
import cn.cloudcat.user.message.human.HumanRoutingService;
import lombok.RequiredArgsConstructor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * CS 聊天动作服务
 * <p>
 * 处理前端的设备操作（网络刷新、流量查询、设备重置）和转人工等动作请求。
 * 动作成功/失败的消息模板由 UserActionTemplateService 管理。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserChatActionService {

    private final UserConversationService conversationService;
    private final AgentTaskManager taskManager;
    private final HumanRoutingService humanRoutingService;
    private final HumanChatSessionManager humanChatSessionManager;
    private final UserDeviceReader userDeviceReader;
    private final ConsultationProfileService profileService;
    private final UserActionTemplateService actionTemplateService;

    /** 启动时加载动作消息模板 */
    @PostConstruct
    void initActionTemplates() {
        actionTemplateService.reload();
    }

    /** 根据 action 编码分发执行设备操作或转人工 */
    public Map<String, Object> executeAction(String conversationId, String userId, String action) {
        log.info("cs action: conversationId={} action={} userId={}", conversationId, action, userId);
        conversationService.getForUser(conversationId, userId);

        List<WifiDeviceSnapshot> devices = userDeviceReader.listByUserId(userId);
        if (devices.isEmpty()) {
            return Map.of("success", false, "message", "未找到绑定的设备");
        }
        WifiDeviceSnapshot device = devices.get(0);

        String code = action != null ? action : "";
        return switch (code) {
            case "REFRESH_NETWORK" -> Map.of("success", true,
                    "message", actionTemplateService.resolveSuccessMessage(code,
                            "网络刷新指令已下发，请稍后查看设备状态"));
            case "CHECK_DATA_USAGE" -> {
                String detail = String.format("当前已用 %.2f GB / 共 %.2f GB",
                        device.getDataUsedGb() != null ? device.getDataUsedGb() : BigDecimal.ZERO,
                        device.getDataTotalGb() != null ? device.getDataTotalGb() : BigDecimal.ZERO);
                yield Map.of("success", true,
                        "message", actionTemplateService.resolveSuccessMessage(code, detail));
            }
            case "RESET_DEVICE" -> Map.of("success", true,
                    "message", actionTemplateService.resolveSuccessMessage(code, "设备重置指令已下发"));
            case "TRANSFER_TO_HUMAN" -> transferHuman(conversationId, userId, device);
            default -> Map.of("success", false, "message", "不支持的操作: " + action);
        };
    }

    /** 转人工：查询用户设备后委托转入工排队流程 */
    public Map<String, Object> transferHuman(String conversationId, String userId) {
        conversationService.getForUser(conversationId, userId);
        List<WifiDeviceSnapshot> devices = userDeviceReader.listByUserId(userId);
        WifiDeviceSnapshot device = devices.isEmpty() ? null : devices.get(0);
        return transferHuman(conversationId, userId, device);
    }

    /** 转人工核心流程：记录尝试 → 路由检查 → 停止 AI → 排队入队 */
    private Map<String, Object> transferHuman(String conversationId, String userId, WifiDeviceSnapshot device) {
        // 记录转人工尝试次数（用于高频用户优先路由）
        humanRoutingService.recordTransferAttempt(conversationId);

        UserConversation conv = conversationService.getForUser(conversationId, userId);
        String intent = conv.getTopicKey() != null ? conv.getTopicKey() : conv.getEntryIntent();
        HumanRoutingService.RoutingResult routing = humanRoutingService.route(conversationId, intent);
        if (!routing.canTransfer()) {
            return Map.of("success", false, "message", routing.message());
        }

        // 记录设备维度的转人工事件
        if (device != null) {
            profileService.recordHumanTransfer(device.getDeviceId(), userId);
        }

        // 停止当前 AI 流式任务
        taskManager.stopTask(conversationId);

        // 已在队列中：返回当前位置
        if (humanChatSessionManager.isInQueue(conversationId)) {
            long position = humanChatSessionManager.getQueuePosition(conversationId);
            return Map.of("success", true,
                    "message", "您已在排队中，当前排在第 " + position + " 位",
                    "queuePosition", position,
                    "channelUrl", routing.channel());
        }

        // 加入排队队列
        long position = humanChatSessionManager.enqueueCustomer(conversationId, userId);
        String msg = routing.highPriority()
                ? "检测到您已多次请求人工服务，已为您优先排队，当前排在第 " + position + " 位"
                : "已为您转接人工客服，当前排在第 " + position + " 位，请耐心等待";

        return Map.of("success", true,
                "message", msg,
                "queuePosition", position,
                "highPriority", routing.highPriority(),
                "channelUrl", routing.channel());
    }
}
