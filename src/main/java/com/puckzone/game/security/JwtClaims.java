package com.puckzone.game.security;

/**
 * Identidad completa que viaja en el JWT de la plataforma: el userId es el
 * claim {@code sub} (UUID de auth) y username/university son claims
 * redundantes que auth incluye al emitir. Se extraen juntos en una sola
 * pasada del token para no parsearlo dos veces.
 */
public record JwtClaims(String userId, String username, String university) {
}
