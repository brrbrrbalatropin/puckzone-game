package com.puckzone.game.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * La solicitud y la amistad son la misma fila: nace PENDING cuando el
 * requester invita y pasa a ACCEPTED cuando el addressee acepta. Única por
 * pareja ordenada; el servicio revisa las dos direcciones antes de crear
 * para que no existan solicitudes cruzadas duplicadas.
 */
@Entity
@Table(name = "friendships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"requester_id", "addressee_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Quien envió la solicitud. */
    @Column(name = "requester_id", nullable = false, length = 36)
    private String requesterId;

    /** Quien la recibe (y decide aceptar o rechazar). */
    @Column(name = "addressee_id", nullable = false, length = 36)
    private String addresseeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FriendshipStatus status;

    @Column(nullable = false)
    private long createdAtEpochMs;

    /** Momento de la aceptación; null mientras siga PENDING. */
    private Long respondedAtEpochMs;
}
