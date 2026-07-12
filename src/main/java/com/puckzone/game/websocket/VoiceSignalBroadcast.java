package com.puckzone.game.websocket;

/**
 * Señal WebRTC retransmitida al rival por su cola personal
 * {@code /user/queue/voice}. Lleva el gameId porque la cola es una sola
 * por usuario: el cliente descarta señales de una sala que ya no juega.
 */
public record VoiceSignalBroadcast(String gameId, String fromUserId, String type, String payload) {
}
