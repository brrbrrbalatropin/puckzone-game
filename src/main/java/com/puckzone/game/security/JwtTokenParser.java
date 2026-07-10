package com.puckzone.game.security;

import com.puckzone.game.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Validación local del JWT con el secreto compartido de la plataforma: de
 * un token firmado sale el userId (claim {@code sub}). Sin fijar el
 * algoritmo (auth firma HS384 en dev y HS512 en prod). Lo usan el
 * handshake del WebSocket y el REST que atiende al frontend; game valida
 * siempre por su cuenta, sin confiar en que la petición "ya pasó" por el
 * gateway.
 */
@Component
public class JwtTokenParser {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenParser.class);

    private final SecretKey key;

    public JwtTokenParser(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** userId del token, o empty si el token falta, está mal firmado o expiró. */
    public Optional<String> userIdFrom(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Token JWT inválido rechazado: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
