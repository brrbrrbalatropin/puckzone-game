package com.puckzone.game.websocket;

import com.puckzone.game.security.StompPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat global del lobby. Cualquier sesión autenticada puede publicar en
 * {@code /app/lobby/chat}; el mensaje validado se retransmite a todos por
 * {@code /topic/lobby/chat} firmado con la identidad del JWT (username y
 * universidad salen del Principal, no del payload: nadie puede hablar por
 * otro). El historial reciente vive en memoria — game corre con réplica
 * única, igual que las salas — y se entrega una sola vez al suscribirse a
 * {@code /app/lobby/chat/history}, así el recién llegado no ve el chat
 * vacío.
 */
@Controller
public class LobbyChatController {

    private static final Logger log = LoggerFactory.getLogger(LobbyChatController.class);

    /** Tope de caracteres por mensaje; el frontend corta al mismo límite. */
    private static final int MAX_TEXT_LENGTH = 200;
    /** Anti-spam: mínimo entre mensajes del mismo usuario. */
    private static final long CHAT_COOLDOWN_MS = 500;
    /** Cuántos mensajes recientes se conservan para los recién llegados. */
    private static final int HISTORY_SIZE = 50;

    private final SimpMessagingTemplate messaging;
    /** Último mensaje por usuario, para el cooldown. */
    private final Map<String, Long> lastMessageAt = new ConcurrentHashMap<>();
    /** Historial reciente; sincronizado sobre sí mismo (escrituras esporádicas). */
    private final Deque<ChatBroadcast> history = new ArrayDeque<>();

    public LobbyChatController(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /**
     * Publica un mensaje en el chat global: se recorta, se valida que no
     * esté vacío ni pase el tope y se aplica el cooldown con el mismo
     * compute atómico de los emotes (dos mensajes seguidos llegan por hilos
     * distintos del broker). No responde nada al remitente: el mensaje le
     * vuelve por el topic como a todos.
     */
    @MessageMapping("/lobby/chat")
    public void send(ChatMessage message, Principal principal) {
        String text = message.text() == null ? "" : message.text().strip();
        if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH) {
            return;
        }

        String userId = principal.getName();
        long now = System.currentTimeMillis();
        boolean[] allowed = {false};
        lastMessageAt.compute(userId, (k, last) -> {
            if (last != null && now - last < CHAT_COOLDOWN_MS) {
                return last;
            }
            allowed[0] = true;
            return now;
        });
        if (!allowed[0]) {
            return;
        }

        // Sesiones con tokens de antes de que el JWT llevara username caen
        // a un nombre neutro; no debería verse tras el próximo login.
        StompPrincipal identity = principal instanceof StompPrincipal p
                ? p
                : new StompPrincipal(userId, null, null);
        String username = identity.username() != null ? identity.username() : "jugador";

        ChatBroadcast broadcast =
                new ChatBroadcast(userId, username, identity.university(), text, now);
        synchronized (history) {
            history.addLast(broadcast);
            if (history.size() > HISTORY_SIZE) {
                history.removeFirst();
            }
        }
        messaging.convertAndSend("/topic/lobby/chat", broadcast);
        log.debug("Chat lobby {}: {} caracteres", username, text.length());
    }

    /**
     * Respuesta directa (una sola vez, solo a esta sesión) al suscribirse a
     * {@code /app/lobby/chat/history}: los últimos mensajes en orden
     * cronológico. Los siguientes llegan en vivo por el topic.
     */
    @SubscribeMapping("/lobby/chat/history")
    public List<ChatBroadcast> history() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }
}
