package com.puckzone.game.websocket;

import com.puckzone.game.room.GameRoomService;
import com.puckzone.game.room.GameStatus;
import com.puckzone.game.room.OpponentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Set;

/**
 * Relay de señalización WebRTC para el chat de voz de una partida. El
 * audio viaja peer-to-peer entre los dos navegadores; por aquí solo pasa
 * la negociación (offer/answer/ICE), que el servidor reenvía al rival sin
 * interpretarla. Solo entre los dos jugadores de una sala humana viva:
 * contra el bot no hay voz y una sala terminada ya no negocia nada.
 */
@Controller
public class VoiceSignalController {

    private static final Logger log = LoggerFactory.getLogger(VoiceSignalController.class);

    private static final Set<String> ALLOWED_TYPES = Set.of("READY", "OFFER", "ANSWER", "ICE", "LEAVE");
    /** Un SDP con candidatos ronda pocos KB; esto frena payloads basura. */
    private static final int MAX_PAYLOAD_CHARS = 30_000;

    private final GameRoomService rooms;
    private final SimpMessagingTemplate messaging;

    public VoiceSignalController(GameRoomService rooms, SimpMessagingTemplate messaging) {
        this.rooms = rooms;
        this.messaging = messaging;
    }

    @MessageMapping("/game/{gameId}/voice")
    public void relay(@DestinationVariable String gameId, VoiceSignalMessage message,
                      Principal principal) {
        if (message.type() == null || !ALLOWED_TYPES.contains(message.type())) {
            return;
        }
        if (message.payload() != null && message.payload().length() > MAX_PAYLOAD_CHARS) {
            return;
        }
        rooms.find(gameId).ifPresent(state -> {
            if (state.getStatus() == GameStatus.FINISHED
                    || state.getOpponentType() == OpponentType.BOT
                    || state.getPlayer2() == null) {
                return;
            }
            String userId = principal.getName();
            String opponentId;
            if (state.getPlayer1().userId().equals(userId)) {
                opponentId = state.getPlayer2().userId();
            } else if (state.getPlayer2().userId().equals(userId)) {
                opponentId = state.getPlayer1().userId();
            } else {
                log.warn("Usuario {} intentó señalizar voz en la sala ajena {}", userId, gameId);
                return;
            }
            messaging.convertAndSendToUser(opponentId, "/queue/voice",
                    new VoiceSignalBroadcast(gameId, userId, message.type(), message.payload()));
        });
    }
}
