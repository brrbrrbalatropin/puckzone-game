package com.puckzone.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Orígenes permitidos para el handshake del WebSocket, bajo
 * {@code puckzone.websocket}. En dev: Vite (5173) y el gateway (8080);
 * en Azure se sobreescribe con el dominio del frontend.
 */
@ConfigurationProperties(prefix = "puckzone.websocket")
public record WebSocketProperties(
        @DefaultValue({"http://localhost:5173", "http://localhost:8080"}) List<String> allowedOrigins
) {
}
