package com.puckzone.game.security;

import java.security.Principal;

/**
 * Identidad de una sesión STOMP: el nombre es el userId (UUID, claim
 * {@code sub} del JWT) y se conservan username/university para features
 * que muestran quién habla (chat del lobby). Spring lo fija en el
 * handshake y lo entrega en cada {@code @MessageMapping}, así el cliente
 * ya no declara quién es.
 */
public record StompPrincipal(String name, String username, String university) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
