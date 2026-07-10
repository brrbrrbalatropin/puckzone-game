package com.puckzone.game.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Valida el JWT en el handshake del WebSocket. El token viaja como
 * {@code /ws?token=<jwt>} porque SockJS no permite el header Authorization
 * (el gateway acepta esa forma por la misma razón y reenvía el query
 * intacto). Sin token válido el handshake muere con 401 y la conexión
 * nunca se abre; con token válido el userId ({@code sub}) queda en los
 * atributos de la sesión para que {@link JwtHandshakeHandler} arme el
 * Principal. La validación en sí la hace {@link JwtTokenParser}.
 */
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = "puckzone.userId";

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final JwtTokenParser tokenParser;

    public JwtHandshakeInterceptor(JwtTokenParser tokenParser) {
        this.tokenParser = tokenParser;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().getFirst("token");
        return tokenParser.userIdFrom(token)
                .map(userId -> {
                    attributes.put(USER_ID_ATTRIBUTE, userId);
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("Handshake WS sin token válido rechazado: {}", request.getURI().getPath());
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                });
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // Nada que hacer después del handshake.
    }
}
