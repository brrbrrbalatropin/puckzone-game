package com.puckzone.game.websocket;

import com.puckzone.game.room.FinishReason;
import com.puckzone.game.room.GameRoomService;
import com.puckzone.game.room.GameState;
import com.puckzone.game.room.GameStatus;
import com.puckzone.game.room.OpponentType;
import com.puckzone.game.room.Player;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El "juego jugable" de punta a punta: clientes STOMP reales (mismo
 * protocolo que usa el frontend) se conectan al servicio completo con su
 * JWT en el query del handshake, cada jugador se une desde SU sesión (la
 * identidad es el Principal del token, ya no viaja en los payloads), la
 * partida arranca sola, el disco se mueve entre broadcasts del loop y los
 * inputs de paleta se aplican validados. Sin token no hay conexión.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Tiempos cortos para probar abandono y barrido sin esperar los
        // reales (la reconexión del test tarda ~100ms, no choca con 2s).
        // Deben ser IDÉNTICOS a los de ActiveGameEndpointTest para que las
        // dos clases compartan el contexto de Spring cacheado.
        properties = {
                "puckzone.game.disconnect-grace-seconds=2",
                "puckzone.game.finished-retention-seconds=1"})
class GameSocketIntegrationTest {

    /** Mismo default dev que application.yaml (y que auth/matchmaking/gateway). */
    private static final String DEV_SECRET = "puckzone-dev-secret-change-me-please-32bytes-min!!";
    private static final String PLAYER1_ID = "6f1f9c1e-8a4b-4b6e-9f0d-2f4f4b1a1111";
    private static final String PLAYER2_ID = "0b7e3d2a-5c9f-4e8b-a1d3-7c6e5d4f2222";

    @LocalServerPort
    private int port;

    @Autowired
    private GameRoomService rooms;

    private String gameId;
    private WebSocketStompClient stompClient;
    private StompSession session1;
    private StompSession session2;
    private final BlockingQueue<GameState> received = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() {
        // Id único por test: las salas del GameRoomService sobreviven entre
        // tests (create es idempotente y el contexto de Spring se comparte).
        gameId = "partida-it-" + UUID.randomUUID();
        rooms.create(gameId,
                new Player(PLAYER1_ID, "daniel", "escuelaing"),
                new Player(PLAYER2_ID, "rival", "unal"),
                OpponentType.HUMAN, false, null);

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void disconnect() {
        for (StompSession session : new StompSession[]{session1, session2}) {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        stompClient.stop();
    }

    @Test
    void partidaCompletaJugable() throws Exception {
        session1 = connectAs(PLAYER1_ID, "daniel");
        session2 = connectAs(PLAYER2_ID, "rival");
        subscribeToGame(session1);

        // Se une el jugador 1 desde su sesión: la sala sigue esperando al 2
        join(session1);
        var waiting = awaitState(state -> true);
        assertEquals(GameStatus.WAITING, waiting.getStatus());

        // Se une el jugador 2 desde la suya: la partida arranca sola
        join(session2);
        var playing = awaitState(state -> state.getStatus() == GameStatus.PLAYING);
        assertNotNull(playing, "la partida nunca pasó a PLAYING");

        // El disco se mueve entre broadcasts (el motor está vivo)
        var before = awaitState(state -> state.getStatus() == GameStatus.PLAYING);
        var after = awaitState(state ->
                state.getPuckX() != before.getPuckX() || state.getPuckY() != before.getPuckY());
        assertNotNull(after, "el disco no se movió entre broadcasts");

        // Input legítimo del jugador 1: la paleta queda donde el mouse
        session1.send("/app/game/" + gameId + "/paddle", new PaddleMoveMessage(200, 100));
        var moved = awaitState(state -> state.getPaddle1X() == 200 && state.getPaddle1Y() == 100);
        assertNotNull(moved, "el movimiento de paleta no se aplicó");

        // Input tramposo: el jugador 1 intenta meterse a la cancha rival
        session1.send("/app/game/" + gameId + "/paddle", new PaddleMoveMessage(750, 250));
        var clamped = awaitState(state -> state.getPaddle1X() == 370);
        assertNotNull(clamped, "el servidor no recortó la coordenada tramposa");
        assertTrue(clamped.getPaddle1X() <= 400, "la paleta invadió la mitad rival");
    }

    @Test
    void partidaSePausaAlCaerseUnJugadorYSeReanudaAlVolver() throws Exception {
        session1 = connectAs(PLAYER1_ID, "daniel");
        session2 = connectAs(PLAYER2_ID, "rival");
        subscribeToGame(session1);
        join(session1);
        join(session2);
        assertNotNull(awaitState(state -> state.getStatus() == GameStatus.PLAYING));

        // El jugador 2 cierra la pestaña: la partida se pausa con ventana de gracia
        session2.disconnect();
        var paused = awaitState(state -> state.getStatus() == GameStatus.PAUSED);
        assertNotNull(paused, "la partida no se pausó al caerse el jugador 2");
        assertTrue(!paused.isPlayer2Connected(), "el jugador caído sigue figurando conectado");
        assertTrue(paused.getGraceDeadlineEpochMs() > System.currentTimeMillis(),
                "la pausa no dejó ventana de gracia hacia el futuro");

        // Vuelve dentro de la ventana (reconectar = volver a hacer join): se reanuda
        session2 = connectAs(PLAYER2_ID, "rival");
        join(session2);
        var resumed = awaitState(state -> state.getStatus() == GameStatus.PLAYING);
        assertNotNull(resumed, "la partida no se reanudó con la reconexión");
        assertEquals(0, resumed.getGraceDeadlineEpochMs(), "la reanudación no limpió la ventana de gracia");
    }

    @Test
    void abandonoSinReconexionDaLaVictoriaAlQueSeQueda() throws Exception {
        session1 = connectAs(PLAYER1_ID, "daniel");
        session2 = connectAs(PLAYER2_ID, "rival");
        subscribeToGame(session1);
        join(session1);
        join(session2);
        assertNotNull(awaitState(state -> state.getStatus() == GameStatus.PLAYING));

        // El jugador 2 se va y NO vuelve: al expirar la gracia gana el 1
        session2.disconnect();
        assertNotNull(awaitState(state -> state.getStatus() == GameStatus.PAUSED));
        var finished = awaitState(state -> state.getStatus() == GameStatus.FINISHED);
        assertNotNull(finished, "la ventana de gracia expirada no cerró la partida");
        assertEquals(PLAYER1_ID, finished.getWinnerId(), "debía ganar el que se quedó esperando");
        assertEquals(FinishReason.DISCONNECT, finished.getFinishReason());
    }

    @Test
    void rendirseTerminaLaPartidaAFavorDelRival() throws Exception {
        session1 = connectAs(PLAYER1_ID, "daniel");
        session2 = connectAs(PLAYER2_ID, "rival");
        subscribeToGame(session2);
        join(session1);
        join(session2);
        assertNotNull(awaitState(state -> state.getStatus() == GameStatus.PLAYING));

        // El jugador 1 se rinde (la confirmación ya la hizo el frontend)
        session1.send("/app/game/" + gameId + "/surrender", Map.of());
        var finished = awaitState(state -> state.getStatus() == GameStatus.FINISHED);
        assertNotNull(finished, "la rendición no terminó la partida");
        assertEquals(PLAYER2_ID, finished.getWinnerId(), "debía ganar el rival del que se rinde");
        assertEquals(FinishReason.SURRENDER, finished.getFinishReason());
    }

    @Test
    void laSalaTerminadaSeBarreDelMapaTrasLaRetencion() throws Exception {
        session1 = connectAs(PLAYER1_ID, "daniel");
        session2 = connectAs(PLAYER2_ID, "rival");
        subscribeToGame(session1);
        join(session1);
        join(session2);
        assertNotNull(awaitState(state -> state.getStatus() == GameStatus.PLAYING));

        session1.send("/app/game/" + gameId + "/surrender", Map.of());
        assertNotNull(awaitState(state -> state.getStatus() == GameStatus.FINISHED));

        // Con retención de 1s (properties del test), el loop la barre solo
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(rooms.find(gameId).isEmpty(),
                        "la sala FINISHED no se barrió del mapa"));
    }

    @Test
    void handshakeSinTokenRechazado() {
        assertThrows(ExecutionException.class, () ->
                stompClient.connectAsync("ws://localhost:" + port + "/ws/websocket",
                        new StompSessionHandlerAdapter() {
                        }).get(5, TimeUnit.SECONDS));
    }

    @Test
    void handshakeConTokenDeOtroSecretoRechazado() {
        String forged = Jwts.builder()
                .subject(PLAYER1_ID)
                .signWith(Keys.hmacShaKeyFor(
                        "otro-secreto-cualquiera-de-al-menos-32-bytes!!!!".getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThrows(ExecutionException.class, () ->
                stompClient.connectAsync("ws://localhost:" + port + "/ws/websocket?token=" + forged,
                        new StompSessionHandlerAdapter() {
                        }).get(5, TimeUnit.SECONDS));
    }

    @Test
    void intrusoConTokenValidoNoJuegaEnSalaAjena() throws Exception {
        session1 = connectAs(PLAYER1_ID, "daniel");
        session2 = connectAs(PLAYER2_ID, "rival");
        subscribeToGame(session1);
        join(session1);
        join(session2);
        assertNotNull(awaitState(state -> state.getStatus() == GameStatus.PLAYING));

        // Un tercero autenticado pero ajeno a la sala intenta mover paleta:
        // el servidor lo ignora y las paletas siguen donde estaban.
        var intruder = connectAs(UUID.randomUUID().toString(), "intruso");
        try {
            intruder.send("/app/game/" + gameId + "/paddle", new PaddleMoveMessage(200, 100));
            var hijacked = awaitState(
                    state -> state.getPaddle1X() == 200 || state.getPaddle2X() == 200, 1_000);
            assertNull(hijacked, "un usuario ajeno a la sala movió una paleta");
        } finally {
            intruder.disconnect();
        }
    }

    /** Conecta una sesión STOMP con un JWT firmado con el secreto dev. */
    private StompSession connectAs(String userId, String username) throws Exception {
        String token = Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("email", username + "@correo.escuelaing.edu.co")
                .claim("university", "escuelaing")
                .signWith(Keys.hmacShaKeyFor(DEV_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        // /ws/websocket = transporte WebSocket crudo del endpoint SockJS
        return stompClient.connectAsync("ws://localhost:" + port + "/ws/websocket?token=" + token,
                new StompSessionHandlerAdapter() {
                }).get(5, TimeUnit.SECONDS);
    }

    private void subscribeToGame(StompSession session) {
        session.subscribe("/topic/game/" + gameId, new StompFrameHandler() {
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

    /** El join ya no lleva payload: la identidad es el Principal de la sesión. */
    private void join(StompSession session) {
        session.send("/app/game/" + gameId + "/join", Map.of());
    }

    private GameState awaitState(Predicate<GameState> condition) throws InterruptedException {
        return awaitState(condition, 5_000);
    }

    /** Espera hasta timeoutMs por un broadcast que cumpla la condición. */
    private GameState awaitState(Predicate<GameState> condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            var state = received.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (state != null && condition.test(state)) {
                return state;
            }
        }
        return null;
    }
}
