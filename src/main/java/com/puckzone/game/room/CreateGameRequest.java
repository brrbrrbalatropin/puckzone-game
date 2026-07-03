package com.puckzone.game.room;

/**
 * Cuerpo del POST /games que envía puckzone-matchmaking al emparejar.
 * {@code player2} llega {@code null} cuando el rival es el bot.
 */
public record CreateGameRequest(
        String matchId,
        Player player1,
        Player player2,
        OpponentType opponentType
) {
}
