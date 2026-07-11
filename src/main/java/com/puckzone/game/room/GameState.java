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
    /** Sala privada entre amigos: se juega igual pero no mueve ELO ni V-D. */
    private boolean friendly;
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

    /**
     * false mientras el disco fantasma está oculto. Con el fantasma
     * activo, cada contacto lo destella visible un instante (el flash) y
     * vuelve a ocultarse; sin fantasma siempre es true.
     */
    private boolean puckVisible;

    /** Hasta cuándo dura el disco fantasma (0 = sin fantasma). */
    private long ghostUntilEpochMs;

    /** Fin del destello post-rebote durante el fantasma (lo apaga el PowerManager). */
    private long ghostFlashUntilEpochMs;

    /** Caos recogido: el próximo golpe de paleta sale al doble. */
    private boolean chaosArmed;

    /** El disco viaja a velocidad caótica (2x) hasta el próximo golpe o gol. */
    private boolean chaosShot;

    /** Radios efectivos de paleta (el escudo dobla el del dueño). */
    private double paddle1Radius;
    private double paddle2Radius;

    /** Reloj del spawner de poderes (interno del PowerManager). */
    private long lastPowerSpawnEpochMs;

    /**
     * Pausa de anuncio: el saque no sale antes de este instante (el disco
     * espera quieto en el centro). El frontend muestra el banner de gol o
     * de arranque mientras no se cumpla.
     */
    private long serveAtEpochMs;

    /** Quién anotó el último gol (1|2; 0 = aún nadie), para el banner. */
    private int lastScorer;

    /** Dirección del saque diferido (-1 izquierda, 1 derecha, 0 = aleatoria). */
    private int pendingServeDirection;

    /**
     * Detector de encierro del disco (la firma del bug es la normal del
     * contacto repitiéndose espejada o idéntica en el mismo punto, tick
     * tras tick): última normal de colisión con paleta, dónde ocurrió y
     * cuántos contactos lleva la racha. Interno del motor.
     */
    private double pinchNormalX;
    private double pinchNormalY;
    private double pinchPuckX;
    private double pinchPuckY;
    private int pinchStreak;

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
