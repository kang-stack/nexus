package cn.cloudcat.user.diagnosis;

import cn.cloudcat.user.diagnosis.model.DeviceFacts;
import cn.cloudcat.user.diagnosis.model.DiagnosisResult;
import cn.cloudcat.user.diagnosis.model.Finding;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.dmn.engine.DmnDecision;
import org.camunda.bpm.dmn.engine.DmnDecisionResult;
import org.camunda.bpm.dmn.engine.DmnDecisionResultEntries;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * DMN 诊断服务：两阶段评估（Finding 发现 → Priority 聚合）生成 DiagnosisResult。
 * <p>
 * 第一阶：evaluateFindings 将设备事实输入 network_finding DMN 表，输出 Finding 列表。
 * 第二阶：aggregate 将 Finding 转换为布尔标记输入 network_priority DMN 表，输出主根因。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class DmnDiagnosisService {

    /** network_finding DMN 决策 key */
    private static final String FINDING_DECISION = "network_finding";
    /** network_priority DMN 决策 key */
    private static final String PRIORITY_DECISION = "network_priority";

    private final RuleSceneRegistry sceneRegistry;
    private final DmnEngineHolder dmnEngineHolder;

    public DmnDiagnosisService(RuleSceneRegistry sceneRegistry, DmnEngineHolder dmnEngineHolder) {
        this.sceneRegistry = sceneRegistry;
        this.dmnEngineHolder = dmnEngineHolder;
    }

    /** 第一阶：根据设备事实评估 DMN finding 表，输出发现列表 */
    public List<Finding> evaluateFindings(String sceneCode, DeviceFacts facts) {
        RuleSceneRegistry.ScenePaths paths = sceneRegistry.resolve(sceneCode);
        DmnDecision decision = dmnEngineHolder.loadDecision(paths.findingDmnPath(), decisionKeyForFinding(sceneCode));
        Map<String, Object> variables = new LinkedHashMap<>(facts.values());

        DmnDecisionResult result = dmnEngineHolder.engine().evaluateDecision(decision, variables);
        List<Finding> findings = new ArrayList<>();
        for (Map<String, Object> rule : result.getResultList()) {
            String code = stringVal(rule, "findingCode");
            if (code == null || code.isBlank()) {
                continue;
            }
            findings.add(new Finding(
                    code,
                    stringVal(rule, "level"),
                    stringVal(rule, "message"),
                    Map.of("sceneCode", sceneCode)
            ));
        }
        log.debug("DMN findings scene={} count={}", sceneCode, findings.size());
        return findings;
    }

    /** 第二阶：将发现列表转为布尔标记输入 priority DMN 表，聚合出主根因 */
    public DiagnosisResult aggregate(String sceneCode, List<Finding> findings) {
        RuleSceneRegistry.ScenePaths paths = sceneRegistry.resolve(sceneCode);
        DmnDecision decision = dmnEngineHolder.loadDecision(paths.priorityDmnPath(), decisionKeyForPriority(sceneCode));

        // 收集所有发现编码
        Set<String> codes = new HashSet<>();
        for (Finding f : findings) {
            codes.add(f.factKey());
        }

        // 将发现编码转为布尔标记变量
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("hasNotActivated", codes.contains("NOT_ACTIVATED"));
        variables.put("hasNotRealname", codes.contains("NOT_REALNAME") || codes.contains("PARTIAL_REALNAME"));
        variables.put("hasServiceExpired", codes.contains("SERVICE_EXPIRED"));
        variables.put("hasDataExhausted", codes.contains("DATA_EXHAUSTED"));
        variables.put("hasOffline", codes.contains("OFFLINE"));
        variables.put("hasSignalWeak", codes.contains("SIGNAL_WEAK"));
        variables.put("hasRefundProcessing", codes.contains("REFUND_PROCESSING"));
        variables.put("hasRefundDone", codes.contains("REFUND_DONE"));
        variables.put("hasRefundNoTicket", codes.contains("REFUND_NO_TICKET"));

        DmnDecisionResult result = dmnEngineHolder.engine().evaluateDecision(decision, variables);
        DmnDecisionResultEntries single = result.getSingleResult();
        String primary = single != null ? stringVal(single, "primaryRootCause") : "UNKNOWN";
        String label = single != null ? stringVal(single, "label") : null;

        // 将所有发现按优先级排列
        List<DiagnosisResult.RankedCause> ranked = new ArrayList<>();
        int pri = 1;
        for (Finding f : findings) {
            ranked.add(new DiagnosisResult.RankedCause(f.factKey(), pri++, f.message()));
        }
        if (label != null && primary != null) {
            log.info("DMN primaryRootCause={} scene={}", primary, sceneCode);
        }
        return new DiagnosisResult(primary, ranked, paths.versionTag());
    }

    /** 便捷方法：从设备事实直接执行完整诊断流水线（finding + aggregate） */
    public DiagnosisResult diagnose(String sceneCode, DeviceFacts facts) {
        List<Finding> findings = evaluateFindings(sceneCode, facts);
        return aggregate(sceneCode, findings);
    }

    private String decisionKeyForFinding(String sceneCode) {
        return switch (sceneCode) {
            case "NETWORK" -> FINDING_DECISION;
            default -> sceneCode.toLowerCase() + "_finding";
        };
    }

    private String decisionKeyForPriority(String sceneCode) {
        return switch (sceneCode) {
            case "NETWORK" -> PRIORITY_DECISION;
            default -> sceneCode.toLowerCase() + "_priority";
        };
    }

    private static String stringVal(Map<String, Object> rule, String key) {
        Object v = rule.get(key);
        return v != null ? String.valueOf(v) : null;
    }

    private static String stringVal(DmnDecisionResultEntries entry, String key) {
        Object v = entry.get(key);
        return v != null ? String.valueOf(v) : null;
    }
}
