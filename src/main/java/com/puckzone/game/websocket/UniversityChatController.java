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
 * Chat exclusivo de cada universidad, espejo del chat global del lobby.
 * La universidad NUNCA viaja en el payload: sale del JWT de la sesión, así
 * nadie puede escribir en el canal ajeno; y el {@link UniversityChannelGuard}
 * impide siquiera escucharlo. Publica en
 * {@code /topic/lobby/chat/university/{uni}} con historial en memoria por
 * universidad (réplica única, igual que el global).
 */
@Controller
public class UniversityChatController {

    private static final Logger log = LoggerFactory.getLogger(UniversityChatController.class);

    private static final int MAX_TEXT_LENGTH = 200;
    private static final long CHAT_COOLDOWN_MS = 500;
    private static final int HISTORY_SIZE = 50;

    private final SimpMessagingTemplate messaging;
    /** Cooldown propio del canal (independiente del chat global). */
    private final Map<String, Long> lastMessageAt = new ConcurrentHashMap<>();
    /** Historial reciente por universidad; cada deque sincronizado sobre sí mismo. */
    private final Map<String, Deque<ChatBroadcast>> historyByUniversity = new ConcurrentHashMap<>();

    public UniversityChatController(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @MessageMapping("/lobby/chat/university")
    public void send(ChatMessage message, Principal principal) {
        if (!(principal instanceof StompPrincipal identity) || identity.university() == null) {
            return;
        }
        String text = message.text() == null ? "" : message.text().strip();
        if (text.isEmpty() || text.length() > MAX_TEXT_LENGTH) {
            return;
        }

        long now = System.currentTimeMillis();
        boolean[] allowed = {false};
        lastMessageAt.compute(identity.getName(), (k, last) -> {
            if (last != null && now - last < CHAT_COOLDOWN_MS) {
                return last;
            }
            allowed[0] = true;
            return now;
        });
        if (!allowed[0]) {
            return;
        }

        String username = identity.username() != null ? identity.username() : "jugador";
        ChatBroadcast broadcast =
                new ChatBroadcast(identity.getName(), username, identity.university(), text, now);

        Deque<ChatBroadcast> history = historyByUniversity
                .computeIfAbsent(identity.university(), k -> new ArrayDeque<>());
        synchronized (history) {
            history.addLast(broadcast);
            if (history.size() > HISTORY_SIZE) {
                history.removeFirst();
            }
        }
        messaging.convertAndSend(
                UniversityChannelGuard.UNIVERSITY_TOPIC_PREFIX + identity.university(), broadcast);
        log.debug("Chat {}: {} caracteres de {}", identity.university(), text.length(), username);
    }

    /** Historial del canal de LA PROPIA universidad, una vez al suscribirse. */
    @SubscribeMapping("/lobby/chat/university/history")
    public List<ChatBroadcast> history(Principal principal) {
        if (!(principal instanceof StompPrincipal identity) || identity.university() == null) {
            return List.of();
        }
        Deque<ChatBroadcast> history = historyByUniversity.get(identity.university());
        if (history == null) {
            return List.of();
        }
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }
}
