package com.puckzone.game.room;

import com.puckzone.game.power.ActiveEffect;
import com.puckzone.game.power.PowerPickup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    /** Pickup de poder en el tablero (null si no hay); parpadea hasta su activeFrom. */
    private PowerPickup pickup;

    /** Efectos con duración activos: obstáculo, zonas y escudo (este para el HUD). */
    private List<ActiveEffect> effects;

    /** false mientras dura el disco fantasma; cualquier rebote lo revela. */
    private boolean puckVisible;

    /** Caos recogido: el próximo golpe de paleta sale al doble. */
    private boolean chaosArmed;

    /** El disco viaja a velocidad caótica (2x) hasta el próximo golpe o gol. */
    private boolean chaosShot;

    /** Radios efectivos de paleta (el escudo dobla el del dueño). */
    private double paddle1Radius;
    private double paddle2Radius;

    /** Reloj del spawner de poderes (interno del PowerManager). */
    private long lastPowerSpawnEpochMs;

    /** Epoch ms de creación de la sala; para barrer WAITING huérfanas. */
    private long createdAtEpochMs;

    /** Epoch ms del arranque (paso a PLAYING); para la duración del reporte a ranking. */
    private long startedAtEpochMs;

    /** Epoch ms del fin (paso a FINISHED); arranca la retención antes del barrido. */
    private long finishedAtEpochMs;

    /**
     * Epoch ms hasta el que se espera la reconexión del jugador caído
     * (solo con status PAUSED). El frontend puede pintar la cuenta
     * regresiva con esto; quién falta se ve en los flags de conexión.
     */
    private long graceDeadlineEpochMs;

    /**
     * userId del ganador cuando la partida está FINISHED; null si ganó el
     * bot o si ambos humanos abandonaron. Explícito porque en un forfeit
     * el marcador no determina al ganador.
     */
    private String winnerId;

    /** Cómo terminó la partida (solo con status FINISHED). */
    private FinishReason finishReason;

    /** ¿Están conectados todos los humanos que la partida necesita? */
    public boolean allPlayersConnected() {
        return player1Connected && (opponentType == OpponentType.BOT || player2Connected);
    }
}
