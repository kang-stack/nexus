package cn.cloudcat.ai.spi;

import reactor.core.publisher.Flux;

public interface AiRagService {

    Flux<String> streamChat(RagRequest request);
}
