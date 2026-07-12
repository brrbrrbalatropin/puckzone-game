package com.puckzone.game.config;

import com.puckzone.game.security.JwtHandshakeHandler;
import com.puckzone.game.security.JwtHandshakeInterceptor;
import com.puckzone.game.security.JwtTokenParser;
import com.puckzone.game.websocket.UniversityChannelGuard;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
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
    private final JwtTokenParser tokenParser;

    public WebSocketConfig(WebSocketProperties properties, JwtTokenParser tokenParser) {
        this.properties = properties;
        this.tokenParser = tokenParser;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(properties.allowedOrigins().toArray(String[]::new))
                .setHandshakeHandler(new JwtHandshakeHandler())
                .addInterceptors(new JwtHandshakeInterceptor(tokenParser))
                .withSockJS()
                // El CORS del sistema es responsabilidad exclusiva del gateway.
                // Sin esto, SockJS agrega sus propios headers CORS en /ws/info
                // y el navegador rechaza la respuesta por venir duplicados.
                .setSuppressCors(true);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /queue habilita las colas personales (/user/queue/dm) de los DMs.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // El canal de cada universidad solo admite suscriptores de esa
        // universidad (la del JWT); el resto de topics no cambian.
        registration.interceptors(new UniversityChannelGuard());
    }
}
