package com.puckzone.game.config;

import com.puckzone.game.security.JwtHandshakeHandler;
import com.puckzone.game.security.JwtHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP sobre SockJS. El cliente se conecta a {@code /ws?token=<jwt>},
 * envía sus inputs a destinos {@code /app/**} (llegan a los
 * {@code @MessageMapping}) y recibe el estado por suscripciones a
 * {@code /topic/**}. El servidor es autoritativo: el broker simple solo
 * redistribuye lo que publica el motor, nunca estado calculado por un
 * cliente. La identidad de cada sesión sale del JWT del handshake, no de
 * los payloads.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;
    private final JwtProperties jwtProperties;

    public WebSocketConfig(WebSocketProperties properties, JwtProperties jwtProperties) {
        this.properties = properties;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new))
                .setHandshakeHandler(new JwtHandshakeHandler())
                .addInterceptors(new JwtHandshakeInterceptor(jwtProperties))
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
