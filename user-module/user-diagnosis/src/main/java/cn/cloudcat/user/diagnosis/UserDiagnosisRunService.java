package cn.cloudcat.user.diagnosis;

import cn.cloudcat.user.diagnosis.model.DeviceFacts;
import cn.cloudcat.user.diagnosis.model.DiagnosisResult;
import cn.cloudcat.user.diagnosis.model.Finding;
import cn.cloudcat.user.diagnosis.entity.UserDiagnosisRun;
import cn.cloudcat.common.core.id.SnowflakeIdGenerator;
import cn.cloudcat.user.diagnosis.mapper.UserDiagnosisRunMapper;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 诊断运行记录服务：将每次 DMN 诊断的输入快照、发现列表和聚合结果写入 acc_user_diagnosis_run 表。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserDiagnosisRunService {

    private final UserDiagnosisRunMapper diagnosisRunMapper;

    /** 保存一次诊断运行记录，返回生成的记录 ID */
    @Transactional
    public String save(String conversationId, Long userId, String deviceSn,
                       Map<String, Object> snapshot, List<Finding> findings,
                       DiagnosisResult result) {
        String id = SnowflakeIdGenerator.getInstance().nextIdStr();
        UserDiagnosisRun run = new UserDiagnosisRun();
        run.setId(id);
        run.setConversationId(conversationId);
        run.setUserId(userId != null ? userId : 0L);
        run.setDeviceSn(deviceSn != null && !deviceSn.isBlank() ? deviceSn : "-");
        // 将设备事实快照序列化为 JSON
        run.setSnapshotJson(JSON.toJSONString(snapshot != null ? snapshot : Map.of()));
        run.setFindingsJson(findings != null ? JSON.toJSONString(findings) : null);
        run.setReportJson(JSON.toJSONString(result));
        run.setRuleVersion(result.ruleVersion() != null ? result.ruleVersion() : "unknown");
        run.setCreatedAt(LocalDateTime.now());
        diagnosisRunMapper.insert(run);
        log.info("acc_user_diagnosis_run saved id={} conv={} primary={}", id, conversationId, result.primaryRootCause());
        return id;
    }
}
