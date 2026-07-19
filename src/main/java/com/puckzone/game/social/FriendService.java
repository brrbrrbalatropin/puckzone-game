package com.puckzone.game.social;

import com.puckzone.game.social.FriendViews.FriendView;
import com.puckzone.game.social.FriendViews.Overview;
import com.puckzone.game.social.FriendViews.RequestView;
import com.puckzone.game.social.FriendViews.SearchView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reglas del sistema de amigos. La solicitud se envía por username contra
 * el directorio (quien nunca ha entrado al lobby no es encontrable); una
 * solicitud cruzada — B invita a A cuando A→B ya estaba pendiente — se
 * convierte en aceptación automática en vez de fallar. Rechazar, cancelar
 * y eliminar amigo son la misma operación: borrar la fila, cosa que
 * cualquiera de los dos participantes puede hacer y deja la pareja libre
 * para volver a invitarse.
 */
@Service
public class FriendService {

    private final FriendshipRepository friendships;
    private final PlayerDirectoryRepository directory;
    private final PresenceService presence;

    public FriendService(FriendshipRepository friendships,
                         PlayerDirectoryRepository directory,
                         PresenceService presence) {
        this.friendships = friendships;
        this.directory = directory;
        this.presence = presence;
    }

    @Transactional
    public RequestView sendRequest(String requesterId, String targetUsername) {
        String username = targetUsername == null ? "" : targetUsername.strip();
        PlayerDirectoryEntry target = directory.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new SocialException(HttpStatus.NOT_FOUND,
                        "No existe un jugador con ese username (debe haber entrado al lobby al menos una vez)"));
        if (target.getUserId().equals(requesterId)) {
            throw new SocialException(HttpStatus.BAD_REQUEST, "No puedes agregarte a ti mismo");
        }

        var existing = friendships.findBetween(requesterId, target.getUserId());
        if (existing.isPresent()) {
            Friendship current = existing.get();
            if (current.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new SocialException(HttpStatus.CONFLICT, "Ya son amigos");
            }
            if (current.getAddresseeId().equals(requesterId)) {
                // El otro ya me había invitado: invitar de vuelta es aceptar.
                // Directo a la mutación (no via accept()): la auto-invocación
                // de un método @Transactional no pasa por el proxy de Spring.
                acceptPending(current, requesterId);
                return toRequestView(current, requesterId);
            }
            throw new SocialException(HttpStatus.CONFLICT, "Ya le enviaste una solicitud a ese jugador");
        }

        Friendship saved = friendships.save(Friendship.builder()
                .requesterId(requesterId)
                .addresseeId(target.getUserId())
                .status(FriendshipStatus.PENDING)
                .createdAtEpochMs(System.currentTimeMillis())
                .build());
        return toRequestView(saved, requesterId);
    }

    @Transactional
    public FriendView accept(String userId, long friendshipId) {
        Friendship friendship = friendships.findById(friendshipId)
                .orElseThrow(() -> new SocialException(HttpStatus.NOT_FOUND, "La solicitud ya no existe"));
        if (!friendship.getAddresseeId().equals(userId)) {
            throw new SocialException(HttpStatus.FORBIDDEN, "Solo el destinatario puede aceptar la solicitud");
        }
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new SocialException(HttpStatus.CONFLICT, "La solicitud ya fue respondida");
        }
        return acceptPending(friendship, userId);
    }

    /** Mutación compartida entre accept() y la aceptación por solicitud cruzada. */
    private FriendView acceptPending(Friendship friendship, String userId) {
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setRespondedAtEpochMs(System.currentTimeMillis());
        friendships.save(friendship);
        return toFriendView(friendship, userId);
    }

    /** Rechaza, cancela o elimina según el estado; solo un participante puede. */
    @Transactional
    public void deleteRelation(String userId, long friendshipId) {
        Friendship friendship = friendships.findById(friendshipId)
                .orElseThrow(() -> new SocialException(HttpStatus.NOT_FOUND, "La relación ya no existe"));
        if (!friendship.getRequesterId().equals(userId) && !friendship.getAddresseeId().equals(userId)) {
            throw new SocialException(HttpStatus.FORBIDDEN, "No participas en esta relación");
        }
        friendships.delete(friendship);
    }

    /** ¿Son amigos aceptados? Lo usan los DMs para cerrar el canal a extraños. */
    @Transactional(readOnly = true)
    public boolean areFriends(String userA, String userB) {
        return friendships.findBetween(userA, userB)
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .isPresent();
    }

    /** El panel izquierdo completo del chat en una sola llamada. */
    @Transactional(readOnly = true)
    public Overview overviewOf(String userId) {
        List<FriendView> friends = friendships.findAcceptedOf(userId).stream()
                .map(f -> toFriendView(f, userId))
                .toList();
        List<RequestView> incoming = friendships
                .findByAddresseeIdAndStatus(userId, FriendshipStatus.PENDING).stream()
                .map(f -> toRequestView(f, userId))
                .toList();
        List<RequestView> outgoing = friendships
                .findByRequesterIdAndStatus(userId, FriendshipStatus.PENDING).stream()
                .map(f -> toRequestView(f, userId))
                .toList();
        return new Overview(friends, incoming, outgoing);
    }

    @Transactional(readOnly = true)
    public List<SearchView> search(String userId, String prefix) {
        String query = prefix == null ? "" : prefix.strip();
        if (query.isEmpty()) {
            return List.of();
        }
        return directory.findTop10ByUsernameStartingWithIgnoreCaseOrderByUsername(query).stream()
                .filter(entry -> !entry.getUserId().equals(userId))
                .map(entry -> new SearchView(entry.getUserId(), entry.getUsername(),
                        entry.getUniversity(), presence.isOnline(entry.getUserId())))
                .toList();
    }

    private FriendView toFriendView(Friendship friendship, String viewerId) {
        String otherId = otherOf(friendship, viewerId);
        PlayerDirectoryEntry other = entryOf(otherId);
        return new FriendView(friendship.getId(), otherId, other.getUsername(),
                other.getUniversity(), presence.isOnline(otherId), other.getLastSeenAtEpochMs());
    }

    private RequestView toRequestView(Friendship friendship, String viewerId) {
        String otherId = otherOf(friendship, viewerId);
        PlayerDirectoryEntry other = entryOf(otherId);
        return new RequestView(friendship.getId(), otherId, other.getUsername(),
                other.getUniversity(), friendship.getCreatedAtEpochMs());
    }

    private static String otherOf(Friendship friendship, String viewerId) {
        return friendship.getRequesterId().equals(viewerId)
                ? friendship.getAddresseeId()
                : friendship.getRequesterId();
    }

    /** El directorio siempre debería tener al otro (entró al lobby para existir aquí). */
    private PlayerDirectoryEntry entryOf(String userId) {
        return directory.findById(userId).orElseGet(() -> PlayerDirectoryEntry.builder()
                .userId(userId).username("jugador").lastSeenAtEpochMs(0).build());
    }
}
