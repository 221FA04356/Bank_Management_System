package com.bank.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Real-time channel. The browser connects to /ws using its login session cookie;
 * each customer can only receive messages sent to their own id (/user/queue/events).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WsConfig implements WebSocketMessageBrokerConfigurer {
    @Override public void configureMessageBroker(MessageBrokerRegistry r) {
        r.enableSimpleBroker("/queue");
        r.setUserDestinationPrefix("/user");
        r.setApplicationDestinationPrefixes("/app");
    }

    @Override public void registerStompEndpoints(StompEndpointRegistry r) {
        r.addEndpoint("/ws")
                .setHandshakeHandler(new DefaultHandshakeHandler() {
                    @Override protected Principal determineUser(ServerHttpRequest req, WebSocketHandler h, Map<String, Object> attrs) {
                        Object uid = attrs.get("uid");
                        return uid == null ? null : (Principal) () -> uid.toString();
                    }
                })
                .addInterceptors(new HttpSessionHandshakeInterceptor(List.of("uid", "role")));
    }
}
