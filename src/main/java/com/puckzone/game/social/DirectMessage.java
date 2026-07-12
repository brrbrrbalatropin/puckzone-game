package com.puckzone.game.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mensaje directo persistente entre dos amigos. La conversationKey son los
 * dos userIds ordenados lexicográficamente y unidos por ":": la misma clave
 * sin importar quién escribió, así el historial de la pareja sale con una
 * sola consulta indexada en vez de un OR de cuatro columnas.
 */
@Entity
@Table(name = "direct_messages",
        indexes = @Index(name = "idx_dm_conversation",
                columnList = "conversation_key, sent_at_epoch_ms"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessage {

    /** Tope de caracteres por mensaje, el mismo del chat global del lobby. */
    public static final int MAX_TEXT_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_key", nullable = false, length = 73)
    private String conversationKey;

    @Column(nullable = false, length = 36)
    private String senderId;

    @Column(nullable = false, length = 36)
    private String recipientId;

    @Column(nullable = false, length = MAX_TEXT_LENGTH)
    private String content;

    @Column(nullable = false)
    private long sentAtEpochMs;

    /** Clave canónica de la pareja: igual la escriba quien la escriba. */
    public static String conversationKeyOf(String userA, String userB) {
        return userA.compareTo(userB) <= 0 ? userA + ":" + userB : userB + ":" + userA;
    }
}
