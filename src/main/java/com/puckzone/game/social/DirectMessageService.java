package com.puckzone.game.social;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mensajes directos: persisten en game_db y solo circulan entre amigos
 * aceptados — el canal está cerrado para extraños en ambos sentidos
 * (escribir y leer historial). El cooldown es el mismo medio segundo del chat
 * del lobby, con el compute atómico de siempre.
 */
@Service
public class DirectMessageService {

    private static final long DM_COOLDOWN_MS = 500;

    private final DirectMessageRepository messages;
    private final FriendService friends;
    private final Map<String, Long> lastMessageAt = new ConcurrentHashMap<>();

    public DirectMessageService(DirectMessageRepository messages, FriendService friends) {
        this.messages = messages;
        this.friends = friends;
    }

    /** Vista pública de un mensaje (idéntica para ambos participantes). */
    public record MessageView(long id, String senderId, String recipientId,
                              String content, long sentAtEpochMs) {
    }

    @Transactional
    public MessageView send(String senderId, String toUserId, String rawText) {
        String text = rawText == null ? "" : rawText.strip();
        if (toUserId == null || toUserId.isBlank() || text.isEmpty()
                || text.length() > DirectMessage.MAX_TEXT_LENGTH) {
            throw new SocialException(HttpStatus.BAD_REQUEST, "Mensaje vacío, muy largo o sin destinatario");
        }
        if (!friends.areFriends(senderId, toUserId)) {
            throw new SocialException(HttpStatus.FORBIDDEN, "Solo puedes escribirle a tus amigos");
        }

        long now = System.currentTimeMillis();
        boolean[] allowed = {false};
        lastMessageAt.compute(senderId, (k, last) -> {
            if (last != null && now - last < DM_COOLDOWN_MS) {
                return last;
            }
            allowed[0] = true;
            return now;
        });
        if (!allowed[0]) {
            throw new SocialException(HttpStatus.TOO_MANY_REQUESTS, "Muy rápido: espera un momento");
        }

        DirectMessage saved = messages.save(DirectMessage.builder()
                .conversationKey(DirectMessage.conversationKeyOf(senderId, toUserId))
                .senderId(senderId)
                .recipientId(toUserId)
                .content(text)
                .sentAtEpochMs(now)
                .build());
        return toView(saved);
    }

    /** Última página de la conversación, en orden cronológico. */
    @Transactional(readOnly = true)
    public List<MessageView> history(String viewerId, String otherId) {
        if (!friends.areFriends(viewerId, otherId)) {
            throw new SocialException(HttpStatus.FORBIDDEN, "Solo puedes ver conversaciones con tus amigos");
        }
        return messages
                .findTop50ByConversationKeyOrderBySentAtEpochMsDesc(
                        DirectMessage.conversationKeyOf(viewerId, otherId))
                .reversed().stream()
                .map(DirectMessageService::toView)
                .toList();
    }

    private static MessageView toView(DirectMessage message) {
        return new MessageView(message.getId(), message.getSenderId(),
                message.getRecipientId(), message.getContent(), message.getSentAtEpochMs());
    }
}
