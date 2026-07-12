package com.puckzone.game.social;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlayerDirectoryRepository extends JpaRepository<PlayerDirectoryEntry, String> {

    /** Búsqueda exacta para enviar solicitud (auth no permite usernames duplicados). */
    Optional<PlayerDirectoryEntry> findByUsernameIgnoreCase(String username);

    /** Autocompletar del buscador de amigos. */
    List<PlayerDirectoryEntry> findTop10ByUsernameStartingWithIgnoreCaseOrderByUsername(String prefix);
}
