package com.puckzone.game.social;

/**
 * Ciclo de vida de una amistad. No hay REJECTED: rechazar una solicitud
 * (o eliminar a un amigo) borra la fila, así la pareja puede volver a
 * invitarse sin arrastrar estados muertos.
 */
public enum FriendshipStatus {
    PENDING,
    ACCEPTED
}
