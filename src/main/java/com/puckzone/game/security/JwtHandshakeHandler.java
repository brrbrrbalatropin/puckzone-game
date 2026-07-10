package com.puckzone.game.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Convierte el userId que {@link JwtHandshakeInterceptor} dejó en los
 * atributos del handshake en el {@link Principal} de la sesión STOMP.
 * Si el interceptor rechazó el handshake este método nunca corre.
 */
public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        return new StompPrincipal((String) attributes.get(JwtHandshakeInterceptor.USER_ID_ATTRIBUTE));
    }
}
