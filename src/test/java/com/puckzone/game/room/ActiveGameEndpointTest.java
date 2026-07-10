package com.puckzone.game.room;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GET /api/game/active de punta a punta: el lobby pregunta con su Bearer
 * token si el usuario tiene una partida viva a la cual volver. El propio
 * servicio valida el token (no confía en el gateway).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Mismas properties que GameSocketIntegrationTest para compartir
        // el contexto de Spring cacheado entre las dos clases.
        properties = "puckzone.game.disconnect-grace-seconds=2")
class ActiveGameEndpointTest {

    /** Mismo default dev que application.yaml (y que auth/matchmaking/gateway). */
    private static final String DEV_SECRET = "puckzone-dev-secret-change-me-please-32bytes-min!!";

    @LocalServerPort
    private int port;

    @Autowired
    private GameRoomService rooms;

    private final RestClient client = RestClient.create();

    @Test
    void sinTokenDevuelve401() {
        assertEquals(401, get(null).status());
    }

    @Test
    void sinPartidaVivaDevuelve204() {
        assertEquals(204, get(tokenFor(UUID.randomUUID().toString())).status());
    }

    @Test
    void conPartidaVivaDevuelveElResumenDesdeMiPerspectiva() {
        String me = UUID.randomUUID().toString();
        String gameId = "partida-activa-" + UUID.randomUUID();
        rooms.create(gameId,
                new Player(me, "daniel", "escuelaing"),
                new Player(UUID.randomUUID().toString(), "rival", "unal"),
                OpponentType.HUMAN);

        var response = get(tokenFor(me));

        assertEquals(200, response.status());
        assertTrue(response.body().contains(gameId), "el resumen no trae el gameId");
        assertTrue(response.body().contains("\"opponentUsername\":\"rival\""),
                "el rival no viene desde la perspectiva del que pregunta");
    }

    private HttpResult get(String token) {
        var request = client.get().uri("http://localhost:" + port + "/api/game/active");
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return request.exchange((req, res) -> new HttpResult(res.getStatusCode().value(),
                new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }

    private String tokenFor(String userId) {
        return Jwts.builder()
                .subject(userId)
                .signWith(Keys.hmacShaKeyFor(DEV_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private record HttpResult(int status, String body) {
    }
}
