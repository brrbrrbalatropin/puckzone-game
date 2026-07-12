package com.puckzone.game.websocket;

import com.puckzone.game.room.GameRoomService;
import com.puckzone.game.room.GameState;
import com.puckzone.game.room.GameStatus;
import com.puckzone.game.room.OpponentType;
import com.puckzone.game.room.Player;
import com.puckzone.game.security.StompPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reglas del relay de voz: solo señales de la lista blanca, solo entre
 * los dos jugadores de una sala humana viva, y el payload viaja intacto
 * hacia la cola personal del rival (el servidor no interpreta SDP/ICE).
 */
class VoiceSignalControllerTest {

    private GameRoomService rooms;
    private SimpMessagingTemplate messaging;
    private VoiceSignalController controller;

    private final StompPrincipal daniel = new StompPrincipal("u1", "daniel", "escuelaing");
    private final StompPrincipal rival = new StompPrincipal("u2", "rival", "unal");
    private final StompPrincipal intruso = new StompPrincipal("u3", "intruso", "sabana");

    @BeforeEach
    void setUp() {
        rooms = mock(GameRoomService.class);
        messaging = mock(SimpMessagingTemplate.class);
        controller = new VoiceSignalController(rooms, messaging);
    }

    private GameState humanRoom(GameStatus status) {
        return GameState.builder()
                .gameId("g1")
                .player1(new Player("u1", "daniel", "escuelaing"))
                .player2(new Player("u2", "rival", "unal"))
                .opponentType(OpponentType.HUMAN)
                .status(status)
                .build();
    }

    @Test
    void reenviaLaOfertaAlRivalConElPayloadIntacto() {
        when(rooms.find("g1")).thenReturn(Optional.of(humanRoom(GameStatus.PLAYING)));

        controller.relay("g1", new VoiceSignalMessage("OFFER", "sdp-offer"), daniel);

        var captor = ArgumentCaptor.forClass(VoiceSignalBroadcast.class);
        verify(messaging).convertAndSendToUser(eq("u2"), eq("/queue/voice"), captor.capture());
        var sent = captor.getValue();
        assertEquals("g1", sent.gameId());
        assertEquals("u1", sent.fromUserId());
        assertEquals("OFFER", sent.type());
        assertEquals("sdp-offer", sent.payload());
    }

    @Test
    void elJugador2SenalaHaciaElJugador1() {
        when(rooms.find("g1")).thenReturn(Optional.of(humanRoom(GameStatus.PLAYING)));

        controller.relay("g1", new VoiceSignalMessage("ANSWER", "sdp-answer"), rival);

        var captor = ArgumentCaptor.forClass(VoiceSignalBroadcast.class);
        verify(messaging).convertAndSendToUser(eq("u1"), eq("/queue/voice"), captor.capture());
        assertEquals("u2", captor.getValue().fromUserId());
    }

    @Test
    void rechazaTiposFueraDeLaListaBlanca() {
        when(rooms.find("g1")).thenReturn(Optional.of(humanRoom(GameStatus.PLAYING)));

        controller.relay("g1", new VoiceSignalMessage("HACK", "x"), daniel);
        controller.relay("g1", new VoiceSignalMessage(null, "x"), daniel);

        verify(messaging, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void rechazaPayloadsDesmedidos() {
        when(rooms.find("g1")).thenReturn(Optional.of(humanRoom(GameStatus.PLAYING)));

        controller.relay("g1", new VoiceSignalMessage("ICE", "x".repeat(30_001)), daniel);

        verify(messaging, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void ignoraAlIntrusoQueNoJuegaEnLaSala() {
        when(rooms.find("g1")).thenReturn(Optional.of(humanRoom(GameStatus.PLAYING)));

        controller.relay("g1", new VoiceSignalMessage("OFFER", "sdp"), intruso);

        verify(messaging, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void noHayVozContraElBot() {
        GameState vsBot = GameState.builder()
                .gameId("g1")
                .player1(new Player("u1", "daniel", "escuelaing"))
                .player2(null)
                .opponentType(OpponentType.BOT)
                .status(GameStatus.PLAYING)
                .build();
        when(rooms.find("g1")).thenReturn(Optional.of(vsBot));

        controller.relay("g1", new VoiceSignalMessage("READY", null), daniel);

        verify(messaging, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void unaSalaTerminadaYaNoNegocia() {
        when(rooms.find("g1")).thenReturn(Optional.of(humanRoom(GameStatus.FINISHED)));

        controller.relay("g1", new VoiceSignalMessage("ICE", "cand"), daniel);

        verify(messaging, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void unaSalaInexistenteNoRevienta() {
        when(rooms.find("nope")).thenReturn(Optional.empty());

        controller.relay("nope", new VoiceSignalMessage("OFFER", "sdp"), daniel);

        verify(messaging, never()).convertAndSendToUser(anyString(), anyString(), any());
    }
}
