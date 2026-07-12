package com.puckzone.game.social;

import java.util.List;

/**
 * DTOs de respuesta del sistema de amigos. En las solicitudes, userId y
 * username son siempre LA OTRA persona (el remitente en las recibidas, el
 * destinatario en las enviadas): el frontend nunca necesita mostrarse a sí
 * mismo en esas listas.
 */
public final class FriendViews {

    private FriendViews() {
    }

    /** Un amigo aceptado, con su presencia para el punto verde. */
    public record FriendView(long friendshipId, String userId, String username,
                             String university, boolean online, long lastSeenAtEpochMs) {
    }

    /** Una solicitud pendiente, vista desde cualquiera de los dos lados. */
    public record RequestView(long friendshipId, String userId, String username,
                              String university, long createdAtEpochMs) {
    }

    /** Panel social completo en una sola llamada. */
    public record Overview(List<FriendView> friends, List<RequestView> incoming,
                           List<RequestView> outgoing) {
    }

    /** Resultado del buscador de jugadores. */
    public record SearchView(String userId, String username, String university, boolean online) {
    }
}
