package com.puckzone.game.social;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /** La relación de una pareja en cualquier dirección y estado (hay a lo sumo una). */
    @Query("""
            select f from Friendship f
            where (f.requesterId = :a and f.addresseeId = :b)
               or (f.requesterId = :b and f.addresseeId = :a)
            """)
    Optional<Friendship> findBetween(@Param("a") String userA, @Param("b") String userB);

    /** Amistades aceptadas del usuario, sin importar quién invitó. */
    @Query("""
            select f from Friendship f
            where f.status = com.puckzone.game.social.FriendshipStatus.ACCEPTED
              and (f.requesterId = :userId or f.addresseeId = :userId)
            """)
    List<Friendship> findAcceptedOf(@Param("userId") String userId);

    /** Solicitudes que este usuario tiene pendientes por responder. */
    List<Friendship> findByAddresseeIdAndStatus(String addresseeId, FriendshipStatus status);

    /** Solicitudes que este usuario envió y siguen sin respuesta. */
    List<Friendship> findByRequesterIdAndStatus(String requesterId, FriendshipStatus status);
}
