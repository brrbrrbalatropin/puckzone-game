package com.puckzone.game.room;

/**
 * Entrada del índice {@code active-game:{userId}} en Redis: qué partida
 * viva tiene un usuario y en qué shard vive. Como Redis es compartido
 * entre shards, cualquier instancia puede responderle al lobby a dónde
 * reconectarse aunque la sala viva en la memoria de otro shard.
 */
public record ActiveGameRef(String gameId, int shard) {
}
