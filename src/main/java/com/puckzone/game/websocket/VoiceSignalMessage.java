package com.puckzone.game.websocket;

/**
 * Señal WebRTC que un jugador envía a {@code /app/game/{id}/voice} para
 * negociar el chat de voz con su rival. El servidor NO interpreta el
 * payload (SDP o candidato ICE serializado): solo lo retransmite. Tipos
 * válidos: READY (mi micrófono está listo), OFFER, ANSWER, ICE y LEAVE
 * (corté la voz / negué el permiso de micrófono).
 */
public record VoiceSignalMessage(String type, String payload) {
}
