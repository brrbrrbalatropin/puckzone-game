package com.puckzone.game.room;

/**
 * Resumen de la partida viva de un usuario, desde SU perspectiva, para la
 * pantalla de reconexión del lobby: "tienes una partida en curso contra
 * {opponentUsername}, ¿volver?". El detalle completo llega por el
 * WebSocket una vez el jugador hace join a {@code gameId}.
 */
public record ActiveGameResponse(
        String gameId,
        GameStatus status,
        OpponentType opponentType,
        String opponentUsername,
        int myScore,
        int opponentScore,
        long graceDeadlineEpochMs
) {

    public static ActiveGameResponse of(GameState state, String userId) {
        boolean isPlayer1 = state.getPlayer1().userId().equals(userId);
        Player opponent = isPlayer1 ? state.getPlayer2() : state.getPlayer1();
        return new ActiveGameResponse(
                state.getGameId(),
                state.getStatus(),
                state.getOpponentType(),
                opponent == null ? null : opponent.username(),
                isPlayer1 ? state.getScore1() : state.getScore2(),
                isPlayer1 ? state.getScore2() : state.getScore1(),
                state.getGraceDeadlineEpochMs());
    }
}
