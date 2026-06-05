package cn.cloudcat.user.knowledge.service;

import cn.cloudcat.user.common.entity.KbBranch;
import cn.cloudcat.user.common.entity.KbReplyScript;
import cn.cloudcat.user.common.mapper.KbBranchMapper;
import cn.cloudcat.user.common.mapper.KbReplyScriptMapper;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * 知识库分支 Excel 批量导入服务。
 * <p>
 * 解析上传的 Excel 文件，将意图-分支-回复关系写入 acc_user_kb_branch 和 acc_user_kb_reply_script 表。
 * 支持新增和更新（按 branchCode 判断是否已存在）。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
@Slf4j
public class BranchExcelImportService {

    private final KbBranchMapper branchMapper;
    private final KbReplyScriptMapper scriptMapper;

    public BranchExcelImportService(KbBranchMapper branchMapper, KbReplyScriptMapper scriptMapper) {
        this.branchMapper = branchMapper;
        this.scriptMapper = scriptMapper;
    }

    /** 解析上传的 Excel 文件并导入知识库分支数据，返回导入统计结果 */
    @Transactional
    public Map<String, Object> importFile(MultipartFile file) throws IOException {
        List<BranchRow> rows = new ArrayList<>();
        // 使用 EasyExcel 流式读取，只收集有意义行
        EasyExcel.read(file.getInputStream(), BranchRow.class, new ReadListener<BranchRow>() {
            @Override
            public void invoke(BranchRow data, AnalysisContext context) {
                if (data.intentCode != null && !data.intentCode.isBlank()) {
                    rows.add(data);
                }
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
            }
        }).sheet().doRead();

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (BranchRow row : rows) {
            try {
                if (row.branchCode == null || row.branchCode.isBlank()) {
                    skipped++;
                    continue;
                }
                // 按 branchCode 判断已存在则更新，否则新增
                KbBranch existing = branchMapper.selectOne(new LambdaQueryWrapper<KbBranch>()
                        .eq(KbBranch::getBranchCode, row.branchCode.trim())
                        .last("LIMIT 1"));
                KbBranch branch = existing != null ? existing : new KbBranch();
                branch.setIntentCode(row.intentCode.trim());
                branch.setBranchCode(row.branchCode.trim());
                branch.setBranchName(row.branchName != null ? row.branchName.trim() : row.branchCode.trim());
                branch.setPriority(row.priority != null ? row.priority : 100);
                branch.setEnabled(1);
                branch.setConditionJson(resolveConditionJson(row));
                if (existing == null) {
                    branchMapper.insert(branch);
                    created++;
                } else {
                    branchMapper.updateById(branch);
                    updated++;
                }
                if (row.replyText != null && !row.replyText.isBlank()) {
                    upsertScript(branch.getId(), row.replyText.trim());
                }
            } catch (Exception e) {
                errors.add(row.branchCode + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", errors.isEmpty());
        result.put("branchesCreated", created);
        result.put("branchesUpdated", updated);
        result.put("skipped", skipped);
        result.put("errors", errors);
        result.put("message", String.format("导入完成：新增 %d，更新 %d，跳过 %d", created, updated, skipped));
        return result;
    }

    /** 插入或更新分支的首条回复脚本（seqNo=1） */
    private void upsertScript(Long branchId, String text) {
        KbReplyScript script = scriptMapper.selectOne(new LambdaQueryWrapper<KbReplyScript>()
                .eq(KbReplyScript::getBranchId, branchId)
                .eq(KbReplyScript::getSeqNo, 1)
                .last("LIMIT 1"));
        if (script == null) {
            script = new KbReplyScript();
            script.setBranchId(branchId);
            script.setSeqNo(1);
            script.setContentType("TEXT");
            script.setTextTemplate(text);
            script.setDelaySeconds(0);
            scriptMapper.insert(script);
        } else {
            script.setTextTemplate(text);
            scriptMapper.updateById(script);
        }
    }

    /** 生成空模板（含示例行） */
    public byte[] buildTemplateWorkbook() {
        BranchRow sample = new BranchRow();
        sample.setIntentCode("网络问题");
        sample.setBranchCode("NET_EXAMPLE");
        sample.setBranchName("示例分支");
        sample.setPrimaryRootCause("UNKNOWN");
        sample.setPriority(100);
        sample.setReplyText("这是示例回复话术，导入时请删除本行或覆盖。");
        sample.setConditionJson(null);
        return writeWorkbook(List.of(sample));
    }

    /** 导出当前全部分支（含首条话术） */
    public byte[] exportBranchesWorkbook() {
        List<KbBranch> branches = branchMapper.selectList(new LambdaQueryWrapper<KbBranch>()
                .orderByAsc(KbBranch::getIntentCode)
                .orderByAsc(KbBranch::getPriority));
        List<BranchRow> rows = new ArrayList<>();
        for (KbBranch branch : branches) {
            BranchRow row = new BranchRow();
            row.setIntentCode(branch.getIntentCode());
            row.setBranchCode(branch.getBranchCode());
            row.setBranchName(branch.getBranchName());
            row.setPriority(branch.getPriority());
            row.setConditionJson(branch.getConditionJson());
            row.setPrimaryRootCause(extractPrimaryRootCause(branch.getConditionJson()));
            KbReplyScript script = scriptMapper.selectOne(new LambdaQueryWrapper<KbReplyScript>()
                    .eq(KbReplyScript::getBranchId, branch.getId())
                    .eq(KbReplyScript::getSeqNo, 1)
                    .last("LIMIT 1"));
            if (script != null && script.getTextTemplate() != null) {
                row.setReplyText(script.getTextTemplate());
            }
            rows.add(row);
        }
        return writeWorkbook(rows);
    }

    private static byte[] writeWorkbook(List<BranchRow> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, BranchRow.class).sheet("acc_user_kb_branch").doWrite(rows);
        return out.toByteArray();
    }

    private static String resolveConditionJson(BranchRow row) {
        if (row.conditionJson != null && !row.conditionJson.isBlank()) {
            return row.conditionJson.trim();
        }
        return buildConditionJson(row.primaryRootCause);
    }

    private static String extractPrimaryRootCause(String conditionJson) {
        if (conditionJson == null || conditionJson.isBlank()) {
            return null;
        }
        try {
            JSONObject root = JSON.parseObject(conditionJson);
            if (root.getJSONArray("items") == null) {
                return null;
            }
            for (Object item : root.getJSONArray("items")) {
                if (item instanceof JSONObject obj
                        && "primaryRootCause".equals(obj.getString("field"))) {
                    return obj.getString("val");
                }
            }
        } catch (Exception ignored) {
            // 复杂条件留空，运营用 conditionJson 列维护
        }
        return null;
    }

    /** 根据 primaryRootCause 构建 AND-eq 条件 JSON */
    private static String buildConditionJson(String primaryRootCause) {
        if (primaryRootCause == null || primaryRootCause.isBlank()) {
            return null;
        }
        JSONObject root = new JSONObject();
        root.put("match", "AND");
        root.put("items", List.of(Map.of(
                "field", "primaryRootCause",
                "op", "eq",
                "val", primaryRootCause.trim()
        )));
        return JSON.toJSONString(root);
    }

    /** Excel 行数据模型 */
    @com.alibaba.excel.annotation.ExcelIgnoreUnannotated
    public static class BranchRow {
        @com.alibaba.excel.annotation.ExcelProperty("意图编码")
        private String intentCode;
        @com.alibaba.excel.annotation.ExcelProperty("分支编码")
        private String branchCode;
        @com.alibaba.excel.annotation.ExcelProperty("分支名称")
        private String branchName;
        @com.alibaba.excel.annotation.ExcelProperty("主因代码")
        private String primaryRootCause;
        @com.alibaba.excel.annotation.ExcelProperty("优先级")
        private Integer priority;
        @com.alibaba.excel.annotation.ExcelProperty("回复话术")
        private String replyText;
        @com.alibaba.excel.annotation.ExcelProperty("条件JSON")
        private String conditionJson;

        public String getIntentCode() { return intentCode; }
        public void setIntentCode(String intentCode) { this.intentCode = intentCode; }
        public String getBranchCode() { return branchCode; }
        public void setBranchCode(String branchCode) { this.branchCode = branchCode; }
        public String getBranchName() { return branchName; }
        public void setBranchName(String branchName) { this.branchName = branchName; }
        public String getPrimaryRootCause() { return primaryRootCause; }
        public void setPrimaryRootCause(String primaryRootCause) { this.primaryRootCause = primaryRootCause; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
        public String getReplyText() { return replyText; }
        public void setReplyText(String replyText) { this.replyText = replyText; }
        public String getConditionJson() { return conditionJson; }
        public void setConditionJson(String conditionJson) { this.conditionJson = conditionJson; }
    }
}
