package com.puckzone.game.room;

import com.puckzone.game.config.GameProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Barrido del mapa de salas en memoria: las FINISHED viven su retención
 * (para que un jugador tardío aún reciba el estado final), las WAITING
 * huérfanas expiran, y nada más se toca. Cada eliminación publica su
 * evento para que otros limpien estado asociado.
 */
class GameRoomServiceTest {

    /** Retención FINISHED 60s, timeout WAITING 300s (defaults de producción). */
    private final GameProperties props =
            new GameProperties(800, 500, 7, 60, 200, 15, 30, 900, 300, 30, 2, 60, 300, 0);

    private final List<Object> publishedEvents = new ArrayList<>();
    private GameStateRepository repository;
    private GameRoomService service;
    private long now;

    @BeforeEach
    void setUp() {
        repository = mock(GameStateRepository.class);
        service = new GameRoomService(props, repository, publishedEvents::add);
        now = System.currentTimeMillis();
    }

    private GameState room(String id) {
        return service.create(id,
                new Player("p1-" + id, "daniel", "escuelaing"),
                new Player("p2-" + id, "rival", "unal"),
                OpponentType.HUMAN, false, null);
    }

    @Test
    void finishedSeBarreSoloCuandoAgotaSuRetencion() {
        var state = room("terminada");
        state.setStatus(GameStatus.FINISHED);
        state.setFinishedAtEpochMs(now);

        service.cleanupExpired(now + 59_000);
        assertTrue(service.find("terminada").isPresent(), "se barrió antes de agotar la retención");

        service.cleanupExpired(now + 61_000);
        assertTrue(service.find("terminada").isEmpty(), "no se barrió agotada la retención");
        assertEquals(List.of(new GameRoomRemovedEvent("terminada")), publishedEvents);
    }

    @Test
    void waitingHuerfanaExpiraPorTimeout() {
        room("huerfana");

        service.cleanupExpired(now + 301_000);

        assertTrue(service.find("huerfana").isEmpty(), "la WAITING huérfana no expiró");
        assertEquals(List.of(new GameRoomRemovedEvent("huerfana")), publishedEvents);
    }

    @Test
    void activeGameLocalGanaConElShardPropio() {
        room("local").setStatus(GameStatus.PLAYING);

        var active = service.activeGameOf("p1-local");

        assertTrue(active.isPresent(), "no encontró la partida local");
        assertEquals("local", active.get().state().getGameId());
        assertEquals(0, active.get().shard(), "el shard local es el de esta instancia");
        // La creación indexó a ambos jugadores para la reconexión entre shards.
        verify(repository).saveActiveRef("p1-local", new ActiveGameRef("local", 0));
        verify(repository).saveActiveRef("p2-local", new ActiveGameRef("local", 0));
    }

    @Test
    void activeGameResuelveDesdeElIndiceLaPartidaDeOtroShard() {
        var remota = GameState.builder()
                .gameId("remota")
                .player1(new Player("viajero", "daniel", "escuelaing"))
                .player2(new Player("rival", "rival", "unal"))
                .status(GameStatus.PLAYING)
                .build();
        when(repository.findActiveRef("viajero"))
                .thenReturn(Optional.of(new ActiveGameRef("remota", 1)));
        when(repository.find("remota")).thenReturn(Optional.of(remota));

        var active = service.activeGameOf("viajero");

        assertTrue(active.isPresent(), "no resolvió la partida del otro shard");
        assertEquals(1, active.get().shard(), "debe devolver el shard dueño de la sala");
    }

    @Test
    void unaRefDeEsteShardSinSalaLocalEsBasuraYNoSeOfrece() {
        when(repository.findActiveRef("fantasma"))
                .thenReturn(Optional.of(new ActiveGameRef("barrida", 0)));

        assertTrue(service.activeGameOf("fantasma").isEmpty(),
                "una ref del propio shard sin sala en memoria no es una partida viva");
    }

    @Test
    void playingYPausedNuncaSeBarren() {
        room("jugando").setStatus(GameStatus.PLAYING);
        room("pausada").setStatus(GameStatus.PAUSED);

        // Un futuro lejano: si el barrido tocara estas salas, aquí caerían.
        service.cleanupExpired(now + 3_600_000);

        assertTrue(service.find("jugando").isPresent());
        assertTrue(service.find("pausada").isPresent());
        assertTrue(publishedEvents.isEmpty());
    }
}
