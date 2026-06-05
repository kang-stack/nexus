package cn.cloudcat.user.message.session;

import cn.cloudcat.user.knowledge.service.SessionTemplateService;
import org.springframework.stereotype.Component;

/**
 * 会话模板桥接组件
 * <p>
 * 桥接 cs-session 模块的 SessionTemplateServiceBridge 接口到
 * icc-knowledge 模块的 SessionTemplateService，解决模块间循环依赖问题。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Component
public class SessionTemplateBridge implements UserSessionTimeoutService.SessionTemplateServiceBridge {

    private final SessionTemplateService sessionTemplateService;

    public SessionTemplateBridge(SessionTemplateService sessionTemplateService) {
        this.sessionTemplateService = sessionTemplateService;
    }

    /** 获取超时提示消息模板，默认兜底文案 */
    @Override
    public String timeoutMessage() {
        return sessionTemplateService.getTemplate("TIMEOUT",
                "由于您长时间未回复，本次会话已自动结束。如需帮助请重新发起咨询。");
    }
}
