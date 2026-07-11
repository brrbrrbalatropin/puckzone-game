package com.puckzone.game.websocket;

import com.puckzone.game.security.StompPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Reglas del chat del lobby: identidad del JWT (no del payload), texto
 * validado, cooldown por usuario e historial reciente para el que llega.
 */
class LobbyChatControllerTest {

    private SimpMessagingTemplate messaging;
    private LobbyChatController controller;

    private final StompPrincipal daniel = new StompPrincipal("u1", "daniel", "escuelaing");

    @BeforeEach
    void setUp() {
        messaging = mock(SimpMessagingTemplate.class);
        controller = new LobbyChatController(messaging);
    }

    @Test
    void publicaConLaIdentidadDelToken() {
        controller.send(new ChatMessage("  hola a todos  "), daniel);

        var captor = ArgumentCaptor.forClass(ChatBroadcast.class);
        verify(messaging).convertAndSend(eq("/topic/lobby/chat"), captor.capture());
        var sent = captor.getValue();
        assertEquals("u1", sent.userId());
        assertEquals("daniel", sent.username());
        assertEquals("escuelaing", sent.university());
        assertEquals("hola a todos", sent.text(), "el texto debe llegar recortado");
    }

    @Test
    void rechazaVacioYDemasiadoLargo() {
        controller.send(new ChatMessage("   "), daniel);
        controller.send(new ChatMessage(null), daniel);
        controller.send(new ChatMessage("x".repeat(201)), daniel);

        verify(messaging, never()).convertAndSend(eq("/topic/lobby/chat"), any(ChatBroadcast.class));
        assertTrue(controller.history().isEmpty());
    }

    @Test
    void cooldownFrenaElSegundoMensajeInmediato() {
        controller.send(new ChatMessage("primero"), daniel);
        controller.send(new ChatMessage("segundo"), daniel);

        assertEquals(1, controller.history().size(), "el segundo mensaje debió caer por cooldown");
        // Otro usuario no comparte el cooldown
        controller.send(new ChatMessage("hola"), new StompPrincipal("u2", "rival", "unal"));
        assertEquals(2, controller.history().size());
    }

    @Test
    void historialCronologicoYAcotado() {
        for (int i = 0; i < 60; i++) {
            // Usuario distinto por mensaje para esquivar el cooldown
            controller.send(new ChatMessage("msg-" + i), new StompPrincipal("u" + i, "user" + i, "uni"));
        }

        var history = controller.history();
        assertEquals(50, history.size(), "el historial debe estar acotado");
        assertEquals("msg-10", history.get(0).text(), "se descartan los más viejos");
        assertEquals("msg-59", history.get(history.size() - 1).text());
    }
}
