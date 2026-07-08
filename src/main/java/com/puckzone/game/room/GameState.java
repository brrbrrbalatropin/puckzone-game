package com.puckzone.game.room;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estado completo de una partida. Mutable a propósito: el motor de física
 * lo actualiza en cada tick (~60/s) y crear un objeto nuevo por tick sería
 * basura innecesaria. Vive en memoria (mapa del {@link GameRoomService}) y
 * se fotografía a Redis en eventos clave vía {@link GameStateRepository}.
 *
 * <p>Posiciones en píxeles sobre el tablero 800x500 (origen arriba-izquierda);
 * velocidades en píxeles/segundo. El jugador 1 defiende la portería izquierda
 * y el 2 la derecha. Si el rival es un bot, {@code player2} es {@code null}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameState {

    private String gameId;
    private Player player1;
    private Player player2;
    private OpponentType opponentType;
    private GameStatus status;

    private double puckX;
    private double puckY;
    private double puckVx;
    private double puckVy;

    private double paddle1X;
    private double paddle1Y;
    private double paddle2X;
    private double paddle2Y;

    private int score1;
    private int score2;

    private boolean player1Connected;
    private boolean player2Connected;

    /** Epoch ms del arranque (paso a PLAYING); para la duración del reporte a ranking. */
    private long startedAtEpochMs;

    /** ¿Están conectados todos los humanos que la partida necesita? */
    public boolean allPlayersConnected() {
        return player1Connected && (opponentType == OpponentType.BOT || player2Connected);
    }
}
