package com.puckzone.game.security;

import com.puckzone.game.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Valida el JWT en el handshake del WebSocket. El token viaja como
 * {@code /ws?token=<jwt>} porque SockJS no permite el header Authorization
 * (el gateway acepta esa forma por la misma razón y reenvía el query
 * intacto). Sin token válido el handshake muere con 401 y la conexión
 * nunca se abre; con token válido el userId ({@code sub}) queda en los
 * atributos de la sesión para que {@link JwtHandshakeHandler} arme el
 * Principal. Validación local con el secreto compartido, sin fijar el
 * algoritmo (auth firma HS384 en dev y HS512 en prod).
 */
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = "puckzone.userId";

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final SecretKey key;

    public JwtHandshakeInterceptor(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            log.warn("Handshake WS sin token rechazado: {}", request.getURI().getPath());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            String userId = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            attributes.put(USER_ID_ATTRIBUTE, userId);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Handshake WS con token inválido rechazado: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Nada que hacer después del handshake.
    }
}
