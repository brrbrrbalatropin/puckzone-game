package com.puckzone.game.physics;

/**
 * Qué pasó en un tick de física, para que el {@link GameLoop} decida si
 * hay que fotografiar el estado a Redis o cerrar la partida.
 */
public enum TickOutcome {
    NONE,
    GOAL,
    FINISHED
}
