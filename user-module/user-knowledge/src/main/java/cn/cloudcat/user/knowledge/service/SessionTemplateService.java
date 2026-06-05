package cn.cloudcat.user.knowledge.service;

import cn.cloudcat.user.common.entity.KbSessionTemplate;
import cn.cloudcat.user.common.mapper.KbSessionTemplateMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

/**
 * 会话阶段模板服务：按模板编码读取 acc_user_kb_session_template 表中的话术文本。
 * 提供 fallback 兜底机制，当模板不存在或未启用时返回默认文本。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
public class SessionTemplateService {

    private final KbSessionTemplateMapper templateMapper;

    public SessionTemplateService(KbSessionTemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    /** 按模板编码查找已启用的模板，未找到时返回 fallback */
    public String getTemplate(String code, String fallback) {
        KbSessionTemplate tpl = templateMapper.selectOne(new LambdaQueryWrapper<KbSessionTemplate>()
                .eq(KbSessionTemplate::getTemplateCode, code)
                .eq(KbSessionTemplate::getEnabled, 1)
                .last("LIMIT 1"));
        return tpl != null && tpl.getTextTemplate() != null ? tpl.getTextTemplate() : fallback;
    }
}
