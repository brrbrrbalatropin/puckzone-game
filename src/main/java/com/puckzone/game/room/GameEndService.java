package com.puckzone.game.room;

import com.puckzone.game.client.RankingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Único lugar donde una partida se cierra por forfeit (abandono o
 * rendición): fija ganador y razón, congela la sala en FINISHED,
 * retransmite el estado final (el game loop ya no emite para una sala que
 * salió de PLAYING) y reporta a ranking. Lo usan el game loop cuando una
 * ventana de gracia expira y el controller STOMP cuando alguien se rinde.
 */
@Service
public class GameEndService {

    private static final Logger log = LoggerFactory.getLogger(GameEndService.class);

    private final GameRoomService rooms;
    private final SimpMessagingTemplate messaging;
    private final RankingClient rankingClient;

    public GameEndService(GameRoomService rooms, SimpMessagingTemplate messaging,
                          RankingClient rankingClient) {
        this.rooms = rooms;
        this.messaging = messaging;
        this.rankingClient = rankingClient;
    }

    /**
     * Cierra una sala PAUSED cuya ventana de gracia expiró sin reconexión.
     * Gana quien siguió conectado; contra el bot gana el bot (winnerId
     * null). Si ambos humanos abandonaron no hay a quién premiar: la
     * partida termina sin ganador y no se reporta a ranking.
     */
    public void finishByGraceExpiry(GameState state) {
        String winnerId = null;
        if (state.isPlayer1Connected()) {
            winnerId = state.getPlayer1().userId();
        } else if (state.getPlayer2() != null && state.isPlayer2Connected()) {
            winnerId = state.getPlayer2().userId();
        }
        boolean report = state.getOpponentType() == OpponentType.BOT || winnerId != null;
        log.info("Sala {} cerrada por abandono: la ventana de gracia expiró", state.getGameId());
        finish(state, winnerId, FinishReason.DISCONNECT, report);
    }

    /** Cierra la partida a favor del rival del que se rinde. */
    public void finishBySurrender(GameState state, String surrenderedUserId) {
        String winnerId = state.getPlayer1().userId().equals(surrenderedUserId)
                ? state.getPlayer2() == null ? null : state.getPlayer2().userId()
                : state.getPlayer1().userId();
        log.info("Sala {} cerrada: {} se rindió", state.getGameId(), surrenderedUserId);
        finish(state, winnerId, FinishReason.SURRENDER, true);
    }

    /**
     * Reporte a ranking fuera del hilo que cierra la partida: al game loop
     * no se le puede colgar una llamada HTTP (un hilo tica TODAS las
     * salas) y el reintento del cliente puede tardar segundos.
     */
    public void reportAsync(GameState state) {
        Thread.startVirtualThread(() -> rankingClient.reportFinished(state));
    }

    private void finish(GameState state, String winnerId, FinishReason reason, boolean report) {
        if (state.getStatus() == GameStatus.FINISHED) {
            return;
        }
        state.setStatus(GameStatus.FINISHED);
        state.setWinnerId(winnerId);
        state.setFinishReason(reason);
        state.setGraceDeadlineEpochMs(0);
        state.setFinishedAtEpochMs(System.currentTimeMillis());
        rooms.snapshot(state);
        rooms.clearActiveIndex(state);
        messaging.convertAndSend("/topic/game/" + state.getGameId(), state);
        if (report) {
            reportAsync(state);
        }
    }
}
