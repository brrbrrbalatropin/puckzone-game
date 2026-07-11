package com.puckzone.game.power;

/**
 * Los 6 poderes del enunciado. Se obtienen recogiendo el pickup del
 * tablero con la paleta y el efecto es inmediato.
 * OBSTACLE:   círculo sólido que rebota el disco, anclado donde estaba el
 *             pickup, por un tiempo.
 * FAST_ZONE:  zona que acelera el disco mientras esté adentro.
 * SLOW_ZONE:  zona que lo frena (recogida cerca del arco propio, defensa).
 * GHOST_PUCK: el disco se vuelve invisible hasta el próximo rebote.
 * SHIELD:     la paleta del que lo recoge dobla su radio por un tiempo.
 * CHAOS:      el próximo golpe de paleta sale al doble de velocidad.
 */
public enum PowerType {
    OBSTACLE,
    FAST_ZONE,
    SLOW_ZONE,
    GHOST_PUCK,
    SHIELD,
    CHAOS
}
