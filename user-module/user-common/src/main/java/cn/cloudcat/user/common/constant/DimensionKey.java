package cn.cloudcat.user.common.constant;

/**
 * Playbook / DMN 维度键名常量。
 * <p>
 * 统一管理 DeviceStateService（生产者）和 DMN XML / DmnDiagnosisService（消费者）
 * 之间的中文维度键名契约，避免硬编码字符串散落导致拼写错误时静默不命中。
 * <p>
 * <b>注意：</b>DMN XML 文件（*.dmn）中的 {@code <inputExpression><text>} 值
 * 必须与此处常量保持一致，修改常量时需同步更新 DMN 文件。
 *
 * @author Kang
 * @since 2026-06-03
 */
public final class DimensionKey {

    private DimensionKey() {
        // 工具类，禁止实例化
    }

    // ---- 设备绑定 ----
    /** 是否绑定设备：是 / 否 */
    public static final String BOUND = "是否绑定设备";

    // ---- 设备状态 ----
    /** 设备状态：未激活 / 可用 / 服务到期 / 流量用完 / 设备禁用 */
    public static final String DEVICE_STATUS = "设备状态";
    /** 在线状态：在线 / 离线 */
    public static final String ONLINE_STATUS = "在线状态";
    /** 激活状态：已激活 / 未激活 */
    public static final String ACTIVATION_STATUS = "激活状态";

    // ---- 实名（与在线状态同为设备维度，供 DMN / acc_user_kb_branch 条件使用，无单独对话填槽流程） ----
    /** 实名状态：未实名 / 部分实名 / 全部实名（公众号接口回传，只读） */
    public static final String REALNAME_STATUS = "实名状态";
    /** 设备实名状态：与 {@link #REALNAME_STATUS} 同值，兼容历史 DMN/分支字段名 */
    public static final String DEVICE_REALNAME_STATUS = "设备实名状态";

    // ---- 网络与信号 ----
    /** 信号值：信号好 / 信号差 */
    public static final String SIGNAL_VALUE = "信号值";
    /** 限速状态：是 / 否 */
    public static final String SPEED_LIMITED = "是否限速";
    /** 限速状态（别名） */
    public static final String SPEED_LIMITED_STATUS = "限速状态";
    /** 当前套餐网络速率：低 / 中 / 高 */
    public static final String PLAN_SPEED = "当前套餐网络速率";
    /** 套餐速率（别名） */
    public static final String PLAN_SPEED_ALT = "套餐速率";

    // ---- 运营商与卡 ----
    /** 当前网络运营商：电信 / 移动 / 联通 */
    public static final String CARRIER = "当前网络运营商";
    /** 启动卡状态：在用 / 未用 */
    public static final String CARD_STATUS = "启动卡状态";
    /** 设备绑定卡数：单卡 / 双卡 */
    public static final String CARD_COUNT = "设备绑定卡数";

    // ---- 套餐与续费 ----
    /** 自动续费状态：是 / 否 */
    public static final String AUTO_RENEW = "自动续费状态";
    /** 断网类型：达量断网 等 */
    public static final String DISCONNECT_TYPE = "断网类型";

    // ---- 设备属性 ----
    /** 设备类型 / 型号 */
    public static final String DEVICE_TYPE = "设备类型";
    /** 区分设备类型（与 DEVICE_TYPE 同值） */
    public static final String DEVICE_TYPE_CLASSIFY = "区分设备类型";
    /** 设备所属商户 */
    public static final String MERCHANT = "设备所属商户";
    /** WiFi 连接数 */
    public static final String WIFI_CONNECTIONS = "WiFi连接数";
    /** WiFi 链接数（别名，与 WIFI_CONNECTIONS 同值） */
    public static final String WIFI_CONNECTIONS_ALT = "WiFi链接数";
    /** 激活时间（天数） */
    public static final String ACTIVATION_DAYS = "激活时间";

    // ---- 用户画像 / 工单（待实现占位键） ----
    /** 区分公众号（占位） */
    public static final String WECHAT_OFFICIAL = "区分公众号";
    /** 区分不同公众号（占位） */
    public static final String WECHAT_OFFICIAL_ALT = "区分不同公众号";
    /** 是否存在开发票工单（占位） */
    public static final String INVOICE_TICKET = "是否存在开发票工单";
    /** 区分退款工单状态（占位） */
    public static final String REFUND_TICKET_STATUS = "区分退款工单状态";
}
