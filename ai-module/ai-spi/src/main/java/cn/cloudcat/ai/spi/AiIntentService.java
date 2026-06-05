package cn.cloudcat.ai.spi;

public interface AiIntentService {

    IntentResult classify(IntentRequest request);
}
