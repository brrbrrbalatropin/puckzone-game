package com.puckzone.game.social;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Espejo local de los jugadores que alguna vez conectaron el WebSocket:
 * se upserta con los claims del JWT en cada conexión. Existe para poder
 * buscar amigos por username sin pedirle endpoints nuevos a auth — si
 * alguien entró al lobby al menos una vez, es encontrable. No es fuente
 * de verdad de identidad (eso sigue siendo auth), solo un directorio.
 */
@Entity
@Table(name = "player_directory")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerDirectoryEntry {

    /** UUID de auth (claim sub), el mismo String opaco de toda la plataforma. */
    @Id
    @Column(length = 36)
    private String userId;

    @Column(nullable = false)
    private String username;

    private String university;

    /** Última conexión al WS; el frontend lo muestra como "visto por última vez". */
    @Column(nullable = false)
    private long lastSeenAtEpochMs;
}
