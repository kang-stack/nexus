package cn.cloudcat.user.message.diagnosis;

import cn.cloudcat.user.message.device.DeviceStateService;

import cn.cloudcat.user.diagnosis.UserDiagnosisRunService;
import cn.cloudcat.user.diagnosis.DmnDiagnosisService;
import cn.cloudcat.user.diagnosis.model.DeviceFacts;
import cn.cloudcat.user.diagnosis.model.DiagnosisResult;
import cn.cloudcat.user.diagnosis.model.Finding;
import cn.cloudcat.user.session.service.UserConversationService;
import cn.cloudcat.user.common.model.WifiDeviceSnapshot;
import cn.cloudcat.user.common.spi.UserDeviceReader;
import cn.cloudcat.user.session.entity.UserConversation;
import cn.cloudcat.user.common.entity.KnowledgeIntent;
import cn.cloudcat.user.common.mapper.KnowledgeIntentMapper;
import cn.cloudcat.user.knowledge.engine.IntentResolverService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备诊断管道（Facts + DMN）
 * <p>
 * 编排设备事实加载 → 意图解析 → DMN 规则评估 → 结果聚合的完整诊断流程。
 * 诊断通过 DmnDiagnosisService 执行 Camunda DMN 决策表评估，
 * 结果持久化到 acc_user_diagnosis_run 表供后续分析。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DiagnosisPipeline {

    private final DeviceStateService deviceStateService;
    private final UserDeviceReader userDeviceReader;
    private final DmnDiagnosisService dmnDiagnosisService;
    private final KnowledgeIntentMapper intentMapper;
    private final IntentResolverService intentResolver;
    private final UserDiagnosisRunService diagnosisRunService;
    private final UserConversationService conversationService;

    /** 加载用户设备事实快照（设备维度 + 用户画像） */
    public FactsSnapshot loadFacts(String userId) {
        Map<String, Object> facts = new LinkedHashMap<>(deviceStateService.buildDeviceFactMap(userId));
        return new FactsSnapshot(facts, resolveDeviceSn(userId));
    }

    /** 无会话 ID 的诊断（内部使用） */
    public DiagnosisContext diagnoseForIntent(String userId, String rawIntent) {
        return diagnoseForIntent(userId, rawIntent, null);
    }

    /** 加载事实后执行诊断 */
    public DiagnosisContext diagnoseForIntent(String userId, String rawIntent, String conversationId) {
        FactsSnapshot snapshot = loadFacts(userId);
        DeviceFacts deviceFacts = new DeviceFacts(snapshot.facts(), snapshot.deviceSn(), Instant.now());
        return diagnoseWithFacts(userId, rawIntent, conversationId, deviceFacts);
    }

    /**
     * 核心诊断流程：
     * 1. 解析意图编码 → 2. 检查是否需要诊断 → 3. DMN 评估 → 4. 结果聚合 → 5. 持久化
     */
    public DiagnosisContext diagnoseWithFacts(String userId, String rawIntent,
                                              String conversationId, DeviceFacts deviceFacts) {
        Map<String, Object> facts = deviceFacts != null ? deviceFacts.values() : Map.of();
        String intentCode = intentResolver.resolve(rawIntent);
        KnowledgeIntent intent = findIntent(intentCode);
        // 意图不需要诊断或无场景编码时，直接返回仅含事实的上下文
        if (intent == null || intent.getNeedDiagnosis() == null || intent.getNeedDiagnosis() != 1
                || intent.getRuleSceneCode() == null || intent.getRuleSceneCode().isBlank()) {
            return new DiagnosisContext(facts, null, null);
        }

        try {
            String sceneCode = intent.getRuleSceneCode();
            // Camunda DMN 规则评估
            List<Finding> findings = dmnDiagnosisService.evaluateFindings(sceneCode, deviceFacts);
            // 聚合发现列表为诊断结果
            DiagnosisResult result = dmnDiagnosisService.aggregate(sceneCode, findings);
            log.info("diagnosis intent={} scene={} primary={}", intentCode, sceneCode, result.primaryRootCause());

            // 持久化诊断运行记录
            if (conversationId != null) {
                persistRun(conversationId, userId, deviceFacts.deviceSn(), facts, findings, result);
            }
            return new DiagnosisContext(facts, result.primaryRootCause(), result);
        } catch (Exception e) {
            log.warn("DMN diagnosis failed intent={} scene={}", intentCode, intent.getRuleSceneCode(), e);
            return new DiagnosisContext(facts, null, null);
        }
    }

    /** 持久化诊断运行记录到 acc_user_diagnosis_run 表 */
    private void persistRun(String conversationId, String userId, String deviceSn,
                            Map<String, Object> facts, List<Finding> findings, DiagnosisResult result) {
        try {
            UserConversation conv = conversationService.getRequired(conversationId);
            Long uid = conv.getUserId();
            String dxId = diagnosisRunService.save(conversationId, uid, deviceSn, facts, findings, result);
            conversationService.updateLatestDiagnosisId(conv, dxId);
        } catch (Exception e) {
            log.warn("failed to persist acc_user_diagnosis_run conv={}", conversationId, e);
        }
    }

    /** 根据意图编码查询启用的 KnowledgeIntent 记录 */
    private KnowledgeIntent findIntent(String intentCode) {
        if (intentCode == null) {
            return null;
        }
        List<KnowledgeIntent> list = intentMapper.selectList(new LambdaQueryWrapper<KnowledgeIntent>()
                .eq(KnowledgeIntent::getIntentCode, intentCode)
                .eq(KnowledgeIntent::getEnabled, 1));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 获取用户第一个绑定设备的 SN */
    private String resolveDeviceSn(String userId) {
        List<WifiDeviceSnapshot> devices = userDeviceReader.listByUserId(userId);
        if (devices.isEmpty()) {
            return null;
        }
        return devices.get(0).getSn();
    }

    /** 设备事实快照 */
    public record FactsSnapshot(Map<String, Object> facts, String deviceSn) {}

    /** 诊断上下文：事实 + 根本原因 + 完整诊断结果 */
    public record DiagnosisContext(
            Map<String, Object> facts,
            String primaryRootCause,
            DiagnosisResult diagnosisResult) {
    }
}
