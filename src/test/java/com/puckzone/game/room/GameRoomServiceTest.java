package com.puckzone.game.room;

import com.puckzone.game.config.GameProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Barrido del mapa de salas en memoria: las FINISHED viven su retención
 * (para que un jugador tardío aún reciba el estado final), las WAITING
 * huérfanas expiran, y nada más se toca. Cada eliminación publica su
 * evento para que otros limpien estado asociado.
 */
class GameRoomServiceTest {

    /** Retención FINISHED 60s, timeout WAITING 300s (defaults de producción). */
    private final GameProperties props =
            new GameProperties(800, 500, 7, 60, 200, 15, 30, 900, 300, 30, 2, 60, 300);

    private final List<Object> publishedEvents = new ArrayList<>();
    private GameRoomService service;
    private long now;

    @BeforeEach
    void setUp() {
        service = new GameRoomService(props, mock(GameStateRepository.class), publishedEvents::add);
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
