package com.puckzone.game.room;

/**
 * Cuerpo del POST /games que envía puckzone-matchmaking al emparejar.
 * {@code player2} llega {@code null} cuando el rival es el bot.
 * {@code friendly} marca las salas privadas entre amigos: la partida corre
 * idéntica pero al reportarla ranking no mueve ELO ni V-D (los payloads de
 * la cola normal no traen el campo y caen a false).
 */
public record CreateGameRequest(
        String matchId,
        Player player1,
        Player player2,
        OpponentType opponentType,
        boolean friendly
) {
}
