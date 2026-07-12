package com.puckzone.game.websocket;

import com.puckzone.game.security.StompPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Canal por universidad: publica en el topic de LA universidad del JWT,
 * ignora sesiones sin universidad y el historial está separado por canal.
 */
class UniversityChatControllerTest {

    private SimpMessagingTemplate messaging;
    private UniversityChatController controller;

    private final StompPrincipal daniel = new StompPrincipal("u1", "daniel", "escuelaing");
    private final StompPrincipal rival = new StompPrincipal("u2", "rival", "unal");

    @BeforeEach
    void setUp() {
        messaging = mock(SimpMessagingTemplate.class);
        controller = new UniversityChatController(messaging);
    }

    @Test
    void publicaEnElTopicDeSuPropiaUniversidad() {
        controller.send(new ChatMessage("  ¡vamos escuela!  "), daniel);

        var captor = ArgumentCaptor.forClass(ChatBroadcast.class);
        verify(messaging).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/lobby/chat/university/escuelaing"),
                captor.capture());
        assertEquals("daniel", captor.getValue().username());
        assertEquals("¡vamos escuela!", captor.getValue().text());
    }

    @Test
    void ignoraSesionesSinUniversidadYTextoInvalido() {
        controller.send(new ChatMessage("hola"), new StompPrincipal("u3", "viejo", null));
        controller.send(new ChatMessage("   "), daniel);
        controller.send(new ChatMessage("x".repeat(201)), daniel);

        verify(messaging, never()).convertAndSend(anyString(), any(ChatBroadcast.class));
    }

    @Test
    void elHistorialEstaSeparadoPorUniversidad() {
        controller.send(new ChatMessage("solo escuela"), daniel);
        controller.send(new ChatMessage("solo unal"), rival);

        assertEquals(1, controller.history(daniel).size());
        assertEquals("solo escuela", controller.history(daniel).getFirst().text());
        assertEquals("solo unal", controller.history(rival).getFirst().text());
        assertTrue(controller.history(new StompPrincipal("u4", "x", null)).isEmpty());
    }

    @Test
    void cooldownPorUsuarioDentroDelCanal() {
        controller.send(new ChatMessage("primero"), daniel);
        controller.send(new ChatMessage("segundo"), daniel);

        assertEquals(1, controller.history(daniel).size());
    }
}
