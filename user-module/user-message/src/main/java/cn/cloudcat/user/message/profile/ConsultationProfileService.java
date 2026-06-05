package cn.cloudcat.user.message.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户咨询画像服务（PRD 5.10 + 设计文档 Phase 5）。
 *
 * 基于 acc_user_consultation_profile 表记录用户的高频咨询、网络问题等标签。
 * 数据由 XXL-Job 定时任务每日刷新，此处提供查询接口。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class ConsultationProfileService {

    private final JdbcTemplate jdbcTemplate;

    public ConsultationProfileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询用户画像（按 deviceId）。
     */
    public Map<String, Object> getProfile(String deviceId) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT * FROM acc_user_consultation_profile WHERE device_id = ?", deviceId);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * 记录一次咨询（每次用户发消息时调用）。
     * 用于 XXL-Job 离线聚合。
     */
    public void recordConsultation(String deviceId, String userId, String intent) {
        try {
            // 使用 upsert 模式
            jdbcTemplate.update(
                    "INSERT INTO acc_user_consultation_profile (device_id, user_id, total_consult_count, last_consult_at) "
                            + "VALUES (?, ?, 1, ?) "
                            + "ON DUPLICATE KEY UPDATE total_consult_count = total_consult_count + 1, "
                            + "last_consult_at = ?",
                    deviceId, userId, LocalDateTime.now(), LocalDateTime.now());

            if (isNetworkIntent(intent)) {
                jdbcTemplate.update(
                        "UPDATE acc_user_consultation_profile SET network_consult_count = network_consult_count + 1 "
                                + "WHERE device_id = ?", deviceId);
            }
        } catch (Exception e) {
            log.warn("Failed to record consultation: deviceId={} userId={}", deviceId, userId, e);
        }
    }

    /**
     * 判断是否为网络相关意图。
     */
    private boolean isNetworkIntent(String intent) {
        return intent != null && (intent.contains("网络") || intent.contains("故障")
                || intent.equals("NETWORK_TROUBLESHOOTING"));
    }

    /**
     * 检查用户是否为高频咨询用户（近 30 天 >= 5 次）。
     */
    public boolean isHighFrequency(String deviceId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT total_consult_count FROM acc_user_consultation_profile WHERE device_id = ?",
                    Integer.class, deviceId);
            return count != null && count >= 5;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查用户是否为网络问题咨询用户（近 30 天网络类占比 >= 60%）。
     */
    public boolean isNetworkIssue(String deviceId) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT total_consult_count, network_consult_count FROM acc_user_consultation_profile WHERE device_id = ?",
                    deviceId);
            Integer total = row.get("total_consult_count") != null
                    ? ((Number) row.get("total_consult_count")).intValue() : 0;
            Integer network = row.get("network_consult_count") != null
                    ? ((Number) row.get("network_consult_count")).intValue() : 0;
            return total > 0 && (double) network / total >= 0.6;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 记录一次转人工（PRD 5.10.1）。
     */
    public void recordHumanTransfer(String deviceId, String userId) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO acc_user_consultation_profile (device_id, user_id, human_transfer_count, last_consult_at) "
                            + "VALUES (?, ?, 1, ?) "
                            + "ON DUPLICATE KEY UPDATE human_transfer_count = human_transfer_count + 1, "
                            + "last_consult_at = ?",
                    deviceId, userId, LocalDateTime.now(), LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Failed to record human transfer: deviceId={} userId={}", deviceId, userId, e);
        }
    }

    /**
     * 刷新用户标签（高頻咨询 / 网络问题咨询），由 XXL-Job 或手动触发。
     * PRD 5.10.1/5.10.2: 近 30 天滚动窗口。
     */
    public void refreshProfileFlags(String deviceId) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT total_consult_count, network_consult_count, human_transfer_count "
                            + "FROM acc_user_consultation_profile WHERE device_id = ?", deviceId);

            int total = row.get("total_consult_count") != null
                    ? ((Number) row.get("total_consult_count")).intValue() : 0;
            int network = row.get("network_consult_count") != null
                    ? ((Number) row.get("network_consult_count")).intValue() : 0;
            int human = row.get("human_transfer_count") != null
                    ? ((Number) row.get("human_transfer_count")).intValue() : 0;

            boolean highFreq = total >= 5;
            boolean networkIssue = total > 0 && (double) network / total >= 0.6;

            jdbcTemplate.update(
                    "UPDATE acc_user_consultation_profile SET high_freq_flag = ?, network_issue_flag = ? WHERE device_id = ?",
                    highFreq ? 1 : 0, networkIssue ? 1 : 0, deviceId);

        } catch (Exception e) {
            log.warn("Failed to refresh profile flags: deviceId={}", deviceId, e);
        }
    }
}
