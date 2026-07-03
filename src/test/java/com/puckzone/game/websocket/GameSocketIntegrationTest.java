package com.puckzone.game.websocket;

import com.puckzone.game.room.GameRoomService;
import com.puckzone.game.room.GameState;
import com.puckzone.game.room.GameStatus;
import com.puckzone.game.room.OpponentType;
import com.puckzone.game.room.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El "juego jugable" de punta a punta: un cliente STOMP real (mismo
 * protocolo que usará el frontend) se conecta al servicio completo,
 * los dos jugadores se unen, la partida arranca sola, el disco se mueve
 * entre broadcasts del loop y los inputs de paleta se aplican validados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameSocketIntegrationTest {

    private static final String GAME_ID = "partida-it";

    @LocalServerPort
    private int port;

    @Autowired
    private GameRoomService rooms;

    private WebSocketStompClient stompClient;
    private StompSession session;
    private final BlockingQueue<GameState> received = new LinkedBlockingQueue<>();

    @BeforeEach
    void connect() throws Exception {
        rooms.create(GAME_ID,
                new Player(1L, "daniel", "escuelaing"),
                new Player(2L, "rival", "unal"),
                OpponentType.HUMAN);

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        // /ws/websocket = transporte WebSocket crudo del endpoint SockJS
        session = stompClient.connectAsync("ws://localhost:" + port + "/ws/websocket",
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/game/" + GAME_ID, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GameState.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((GameState) payload);
            }
        });
    }

    @AfterEach
    void disconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        stompClient.stop();
    }

    @Test
    void partidaCompletaJugable() throws Exception {
        // Se une el jugador 1: la sala sigue esperando al 2
        session.send("/app/game/" + GAME_ID + "/join", new JoinMessage(1L));
        var waiting = awaitState(state -> true);
        assertEquals(GameStatus.WAITING, waiting.getStatus());

        // Se une el jugador 2: la partida arranca sola y el loop transmite
        session.send("/app/game/" + GAME_ID + "/join", new JoinMessage(2L));
        var playing = awaitState(state -> state.getStatus() == GameStatus.PLAYING);
        assertNotNull(playing, "la partida nunca pasó a PLAYING");

        // El disco se mueve entre broadcasts (el motor está vivo)
        var before = awaitState(state -> state.getStatus() == GameStatus.PLAYING);
        var after = awaitState(state ->
                state.getPuckX() != before.getPuckX() || state.getPuckY() != before.getPuckY());
        assertNotNull(after, "el disco no se movió entre broadcasts");

        // Input legítimo del jugador 1: la paleta queda donde el mouse
        session.send("/app/game/" + GAME_ID + "/paddle", new PaddleMoveMessage(1L, 200, 100));
        var moved = awaitState(state -> state.getPaddle1X() == 200 && state.getPaddle1Y() == 100);
        assertNotNull(moved, "el movimiento de paleta no se aplicó");

        // Input tramposo: el jugador 1 intenta meterse a la cancha rival
        session.send("/app/game/" + GAME_ID + "/paddle", new PaddleMoveMessage(1L, 750, 250));
        var clamped = awaitState(state -> state.getPaddle1X() == 370);
        assertNotNull(clamped, "el servidor no recortó la coordenada tramposa");
        assertTrue(clamped.getPaddle1X() <= 400, "la paleta invadió la mitad rival");
    }

    /** Espera hasta 5s por un broadcast que cumpla la condición. */
    private GameState awaitState(Predicate<GameState> condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            var state = received.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (state != null && condition.test(state)) {
                return state;
            }
        }
        return null;
    }
}
