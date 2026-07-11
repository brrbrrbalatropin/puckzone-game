package com.puckzone.game.client;

import com.puckzone.game.config.GameProperties;
import com.puckzone.game.config.RankingReportProperties;
import com.puckzone.game.room.GameState;
import com.puckzone.game.room.OpponentType;
import com.puckzone.game.room.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reporta a puckzone-ranking el resultado de una partida terminada.
 * Un reintento ante fallo: el endpoint es idempotente por matchId, así que
 * repetir nunca duplica. En partidas vs bot el lado del bot va con id null
 * y ranking las registra en el historial sin mover ELO ni contadores.
 */
@Component
public class RankingClient {

    private static final Logger log = LoggerFactory.getLogger(RankingClient.class);
    private static final int ATTEMPTS = 2;

    private final RestClient restClient;
    private final GameProperties gameProperties;

    public RankingClient(RestClient.Builder builder, RankingReportProperties properties,
                         GameProperties gameProperties) {
        var settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout());
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
        this.gameProperties = gameProperties;
    }

    /**
     * Construye el payload winner/loser a partir del estado final y lo
     * envía. El ganador lo dice {@code winnerId} (con un forfeit el
     * marcador no alcanza: el que se fue podía ir ganando); null = ganó el
     * bot. El contrato de ranking exige winnerScore = goles de victoria
     * aunque en un forfeit el ganador tuviera menos; los goles reales del
     * perdedor sí viajan tal cual.
     */
    public void reportFinished(GameState state) {
        boolean player1Won = state.getPlayer1().userId().equals(state.getWinnerId());
        Player winner = player1Won ? state.getPlayer1() : state.getPlayer2();
        Player loser = player1Won ? state.getPlayer2() : state.getPlayer1();
        long duration = state.getStartedAtEpochMs() > 0
                ? Math.max(0, (System.currentTimeMillis() - state.getStartedAtEpochMs()) / 1000)
                : 0;

        var request = new MatchResultRequest(
                state.getGameId(),
                state.getOpponentType() == OpponentType.BOT,
                state.isFriendly(),
                winner == null ? null : winner.userId(),
                loser == null ? null : loser.userId(),
                winner == null ? null : winner.username(),
                loser == null ? null : loser.username(),
                winner == null ? null : winner.university(),
                loser == null ? null : loser.university(),
                gameProperties.goalsToWin(),
                player1Won ? state.getScore2() : state.getScore1(),
                duration);

        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                restClient.post()
                        .uri("/api/ranking/match")
                        .body(request)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Resultado de {} reportado a ranking", state.getGameId());
                return;
            } catch (RestClientException e) {
                log.warn("Reporte de {} a ranking falló (intento {}/{}): {}",
                        state.getGameId(), attempt, ATTEMPTS, e.getMessage());
            }
        }
    }

    /** Contrato de POST /api/ranking/match de puckzone-ranking. */
    record MatchResultRequest(String matchId, boolean vsBot, boolean friendly,
                              String winnerId, String loserId,
                              String winnerUsername, String loserUsername,
                              String winnerUniversity, String loserUniversity,
                              int winnerScore, int loserScore,
                              long gameDuration) {
    }
}
