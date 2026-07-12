package com.puckzone.game.social;

import com.puckzone.game.security.StompPrincipal;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quién está conectado al WebSocket ahora mismo (el punto verde de la
 * lista de amigos) y el alimentador del {@link PlayerDirectoryEntry
 * directorio}: cada conexión upserta username/university desde los claims
 * del JWT, así "agregar amigo por username" encuentra a cualquiera que
 * haya entrado al lobby al menos una vez. Un usuario cuenta como en línea
 * mientras tenga al menos una sesión viva (puede tener dos pestañas).
 */
@Service
public class PresenceService {

    private final PlayerDirectoryRepository directory;
    /** Sesiones WS vivas por userId; sin entrada = desconectado. */
    private final Map<String, Set<String>> sessionsByUser = new ConcurrentHashMap<>();

    public PresenceService(PlayerDirectoryRepository directory) {
        this.directory = directory;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (user == null || sessionId == null) {
            return;
        }
        sessionsByUser.computeIfAbsent(user.getName(), k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);

        // Tokens de antes de que el JWT llevara username no aportan al
        // directorio; no debería verse tras el próximo login.
        if (user instanceof StompPrincipal p && p.username() != null) {
            directory.save(PlayerDirectoryEntry.builder()
                    .userId(p.getName())
                    .username(p.username())
                    .university(p.university())
                    .lastSeenAtEpochMs(System.currentTimeMillis())
                    .build());
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        if (event.getUser() == null) {
            return;
        }
        String userId = event.getUser().getName();
        sessionsByUser.computeIfPresent(userId, (k, sessions) -> {
            sessions.remove(event.getSessionId());
            return sessions.isEmpty() ? null : sessions;
        });
        directory.findById(userId).ifPresent(entry -> {
            entry.setLastSeenAtEpochMs(System.currentTimeMillis());
            directory.save(entry);
        });
    }

    public boolean isOnline(String userId) {
        return sessionsByUser.containsKey(userId);
    }
}
