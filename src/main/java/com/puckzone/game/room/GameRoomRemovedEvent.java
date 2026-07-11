package com.puckzone.game.room;

/**
 * Una sala salió del mapa en memoria (barrido de FINISHED viejas o de
 * WAITING huérfanas). Quien guarde estado por sala fuera del GameState
 * (ej. los cooldowns de emotes del controller STOMP) escucha esto para
 * limpiar lo suyo sin acoplarse al servicio de salas.
 */
public record GameRoomRemovedEvent(String gameId) {
}
