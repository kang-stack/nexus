package cn.cloudcat.user.message.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface TitleSummaryService {

    @SystemMessage("你是对话标题生成助手。根据用户第一句话生成不超过20字的中文标题，不加引号。")
    @UserMessage("请根据以下用户问题生成会话标题：{{it}}")
    String generateTitle(String userQuestion);
}
