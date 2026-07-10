package com.puckzone.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Secreto compartido con auth para validar el JWT localmente (sin llamar
 * de vuelta a auth), bajo {@code puckzone.jwt}. En Azure llega como secret
 * de la Container App ({@code PUCKZONE_JWT_SECRET}); el default es SOLO
 * para dev local, el mismo que usan auth, matchmaking y gateway.
 */
@ConfigurationProperties(prefix = "puckzone.jwt")
public record JwtProperties(
        @DefaultValue("puckzone-dev-secret-change-me-please-32bytes-min!!") String secret
) {
}
