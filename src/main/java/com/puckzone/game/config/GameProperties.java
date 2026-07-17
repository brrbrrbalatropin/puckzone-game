package com.puckzone.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Constantes del juego, ajustables bajo {@code puckzone.game}.
 * Dimensiones en píxeles y velocidades en píxeles/segundo, sobre un
 * tablero de 800x500 donde las porterías están centradas en los
 * extremos izquierdo (jugador 1) y derecho (jugador 2).
 */
@ConfigurationProperties(prefix = "puckzone.game")
public record GameProperties(
        @DefaultValue("800") int boardWidth,
        @DefaultValue("500") int boardHeight,
        @DefaultValue("7") int goalsToWin,
        @DefaultValue("60") int tickRate,
        @DefaultValue("200") int goalWidth,
        @DefaultValue("15") int puckRadius,
        @DefaultValue("30") int paddleRadius,
        @DefaultValue("900") double maxPuckSpeed,
        @DefaultValue("300") double serveSpeed,
        @DefaultValue("30") int disconnectGraceSeconds,
        // Pausa de anuncio tras cada gol (y al arrancar): el disco queda
        // retenido en el centro y el frontend muestra quién anotó.
        @DefaultValue("2") int goalPauseSeconds,
        // Cuánto vive una sala FINISHED en memoria (para que un jugador que
        // llegue tarde aún reciba el estado final) antes de que el barrido
        // la elimine. Redis conserva el snapshot con su propio TTL.
        @DefaultValue("60") int finishedRetentionSeconds,
        // Una WAITING más vieja que esto es huérfana (alguien nunca entró).
        @DefaultValue("300") int waitingTimeoutSeconds,
        // Identidad de esta instancia en el sharding por partida: matchmaking
        // asigna cada sala a un shard y el cliente conecta su WS al shard
        // dueño. Viaja en el índice active-game:{userId} de Redis para que
        // cualquier shard pueda decirle al lobby a dónde reconectarse.
        @DefaultValue("0") int shardId
) {
}
