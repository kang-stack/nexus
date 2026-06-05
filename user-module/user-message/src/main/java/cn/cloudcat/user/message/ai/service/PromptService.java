package cn.cloudcat.user.message.ai.service;

import cn.cloudcat.user.common.constant.WifiIntent;
import cn.cloudcat.user.message.ai.model.IntentRecognitionResult;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PromptService {
    private final Map<WifiIntent, String> promptCache = new ConcurrentHashMap<>();
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public String getPrompt(WifiIntent intent) {
        return promptCache.computeIfAbsent(intent, this::loadPromptFromFile);
    }

    public String getPrompt(IntentRecognitionResult intent) {
        return promptCache.computeIfAbsent(WifiIntent.getIntent(intent.intent()), this::loadPromptFromFile);
    }

    private String loadPromptFromFile(WifiIntent intent) {
        try {
            Resource resource = resolver.getResource("classpath:/prompts/" + intent.getFileName());
            return FileCopyUtils.copyToString(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            if (intent != WifiIntent.CHAT_GENERAL) {
                return getPrompt(WifiIntent.CHAT_GENERAL);
            }
            throw new RuntimeException("默认提示词文件缺失", e);
        }
    }
}
