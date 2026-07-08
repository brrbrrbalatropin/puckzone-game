package com.puckzone.game.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP sobre SockJS. El cliente se conecta a {@code /ws}, envía sus
 * inputs a destinos {@code /app/**} (llegan a los {@code @MessageMapping})
 * y recibe el estado por suscripciones a {@code /topic/**}. El servidor es
 * autoritativo: el broker simple solo redistribuye lo que publica el motor,
 * nunca estado calculado por un cliente.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;

    public WebSocketConfig(WebSocketProperties properties) {
        this.properties = properties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new))
                .withSockJS()
                // El CORS del sistema es responsabilidad exclusiva del gateway.
                // Sin esto, SockJS agrega sus propios headers CORS en /ws/info
                // y el navegador rechaza la respuesta por venir duplicados.
                .setSuppressCors(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
