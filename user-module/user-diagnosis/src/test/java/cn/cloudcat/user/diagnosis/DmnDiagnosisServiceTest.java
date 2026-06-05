package cn.cloudcat.user.diagnosis;

import cn.cloudcat.user.diagnosis.model.DeviceFacts;
import cn.cloudcat.user.diagnosis.model.DiagnosisResult;
import cn.cloudcat.user.diagnosis.model.Finding;
import cn.cloudcat.user.common.constant.DimensionKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DmnDiagnosisServiceTest {

    private DmnDiagnosisService service;

    private DmnEngineHolder holder;

    @BeforeEach
    void setUp() {
        holder = new DmnEngineHolder();
        holder.init();
        service = new DmnDiagnosisService(sceneRegistry("NETWORK"), holder);
    }

    private static RuleSceneRegistry sceneRegistry(String sceneCode) {
        return new RuleSceneRegistry(null) {
            @Override
            public ScenePaths resolve(String code) {
                return switch (code) {
                    case "RECHARGE" -> new ScenePaths(
                            "dmn/recharge/recharge-finding.dmn",
                            "dmn/recharge/recharge-priority.dmn",
                            "test");
                    case "DEVICE" -> new ScenePaths(
                            "dmn/device/device-finding.dmn",
                            "dmn/device/device-priority.dmn",
                            "test");
                    case "REFUND" -> new ScenePaths(
                            "dmn/refund/refund-finding.dmn",
                            "dmn/refund/refund-priority.dmn",
                            "test");
                    default -> new ScenePaths(
                            "dmn/network/network-finding.dmn",
                            "dmn/network/network-priority.dmn",
                            "test");
                };
            }
        };
    }

    @Test
    void offlineDeviceShouldPrimaryOffline() {
        // 验证离线设备DMN诊断主因为OFFLINE
        Map<String, Object> values = baseFacts();
        values.put(DimensionKey.ONLINE_STATUS, "离线");
        DeviceFacts facts = new DeviceFacts(values, "SN001", Instant.now());

        DiagnosisResult result = service.diagnose("NETWORK", facts);
        assertEquals("OFFLINE", result.primaryRootCause());
    }

    @Test
    void notActivatedShouldWinOverOffline() {
        // 验证未激活优先级高于离线（多个Finding聚合时NOT_ACTIVATED优先）
        Map<String, Object> values = baseFacts();
        values.put(DimensionKey.ACTIVATION_STATUS, "未激活");
        values.put(DimensionKey.ONLINE_STATUS, "离线");
        DeviceFacts facts = new DeviceFacts(values, "SN001", Instant.now());

        DiagnosisResult result = service.diagnose("NETWORK", facts);
        assertEquals("NOT_ACTIVATED", result.primaryRootCause());
        assertTrue(result.rankedCauses().size() >= 2);
    }

    @Test
    void signalWeakWhenOnline() {
        // 验证在线但信号差时DMN诊断主因为SIGNAL_WEAK
        Map<String, Object> values = baseFacts();
        values.put(DimensionKey.SIGNAL_VALUE, "信号差");
        DeviceFacts facts = new DeviceFacts(values, "SN001", Instant.now());

        List<Finding> findings = service.evaluateFindings("NETWORK", facts);
        assertFalse(findings.isEmpty());
        assertEquals("SIGNAL_WEAK", service.diagnose("NETWORK", facts).primaryRootCause());
    }

    @Test
    void rechargeSceneServiceExpired() {
        // 验证充值场景下设备服务到期DMN诊断主因为SERVICE_EXPIRED
        Map<String, Object> values = baseFacts();
        values.put(DimensionKey.DEVICE_STATUS, "服务到期");
        DeviceFacts facts = new DeviceFacts(values, "SN001", Instant.now());
        service = new DmnDiagnosisService(sceneRegistry("RECHARGE"), holder);
        assertEquals("SERVICE_EXPIRED", service.diagnose("RECHARGE", facts).primaryRootCause());
    }

    @Test
    void deviceSceneNotActivated() {
        // 验证设备场景下未激活DMN诊断主因为NOT_ACTIVATED
        Map<String, Object> values = baseFacts();
        values.put(DimensionKey.ACTIVATION_STATUS, "未激活");
        DeviceFacts facts = new DeviceFacts(values, "SN001", Instant.now());
        service = new DmnDiagnosisService(sceneRegistry("DEVICE"), holder);
        assertEquals("NOT_ACTIVATED", service.diagnose("DEVICE", facts).primaryRootCause());
    }

    @Test
    void refundSceneNoTicketWhenStatusEmpty() {
        // 验证退款场景下工单状态为空时DMN诊断主因为REFUND_NO_TICKET
        Map<String, Object> values = baseFacts();
        values.put("区分退款工单状态", "");
        DeviceFacts facts = new DeviceFacts(values, "SN001", Instant.now());
        service = new DmnDiagnosisService(sceneRegistry("REFUND"), holder);
        assertEquals("REFUND_NO_TICKET", service.diagnose("REFUND", facts).primaryRootCause());
    }

    @Test
    void refundSceneProcessing() {
        // 验证退款场景下工单处理中DMN诊断主因为REFUND_PROCESSING
        Map<String, Object> values = baseFacts();
        values.put("区分退款工单状态", "处理中");
        DeviceFacts facts = new DeviceFacts(values, "SN001", Instant.now());
        service = new DmnDiagnosisService(sceneRegistry("REFUND"), holder);
        assertEquals("REFUND_PROCESSING", service.diagnose("REFUND", facts).primaryRootCause());
    }

    private Map<String, Object> baseFacts() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(DimensionKey.ACTIVATION_STATUS, "已激活");
        m.put(DimensionKey.REALNAME_STATUS, "全部实名");
        m.put(DimensionKey.DEVICE_STATUS, "可用");
        m.put(DimensionKey.ONLINE_STATUS, "在线");
        m.put(DimensionKey.SIGNAL_VALUE, "信号好");
        return m;
    }
}
