package cn.cloudcat.user.common.constant;

import lombok.Getter;

/**
 * WiFi 客服意图枚举，每种意图绑定对应的提示词模板文件。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Getter
public enum WifiIntent {

    /** 设备使用指导 */
    DEVICE_GUIDANCE("wifi-device-guidance-prompt.txt"),
    /** 网络故障排查 */
    NETWORK_TROUBLESHOOTING("wifi-network-troubleshooting-prompt.txt"),
    /** 账户套餐咨询 */
    ACCOUNT_PLAN("wifi-account-plan-prompt.txt"),
    /** 售后服务（退款等） */
    AFTER_SALES("wifi-after-sales-prompt.txt"),
    /** 人工客服转接 */
    HUMAN_SERVICE("wifi-human-service-prompt.txt"),
    /** 通用闲聊兜底 */
    CHAT_GENERAL("wifi-chat-general-prompt.txt");

    private final String fileName;

    WifiIntent(String fileName) {
        this.fileName = fileName;
    }

    public static WifiIntent getIntent(String intent) {
        return switch (intent) {
            case "开票问题" -> ACCOUNT_PLAN;
            case "充值问题" -> ACCOUNT_PLAN;
            case "设备问题" -> DEVICE_GUIDANCE;
            case "实名认证问题" -> ACCOUNT_PLAN;
            case "实名注销问题" -> ACCOUNT_PLAN;
            case "退款问题" -> AFTER_SALES;
            case "网络问题" -> NETWORK_TROUBLESHOOTING;
            case "人工客服" -> HUMAN_SERVICE;
            default -> CHAT_GENERAL;
        };
    }
}
