package cn.cloudcat.user.message.websocket;

import cn.cloudcat.common.security.jwt.JwtTokenProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final cn.cloudcat.user.message.agent.human.HumanChatWebSocketHandler handler;
    private final JwtTokenProvider jwtTokenProvider;

    public WebSocketConfig(cn.cloudcat.user.message.agent.human.HumanChatWebSocketHandler handler,
                           JwtTokenProvider jwtTokenProvider) {
        this.handler = handler;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/human/agent")
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .setAllowedOrigins("*");
    }
}
