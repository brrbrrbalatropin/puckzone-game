package com.puckzone.game.room;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Punto de entrada HTTP de matchmaking: {@code POST /games} crea la sala.
 * Matchmaking solo mira el código de estado (2xx = confirmada); el cuerpo
 * con el gameId se devuelve para poder probar con curl.
 */
@RestController
public class GameRoomController {

    private final GameRoomService roomService;

    public GameRoomController(GameRoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping("/games")
    public ResponseEntity<?> createGame(@RequestBody CreateGameRequest request) {
        if (request.matchId() == null || request.matchId().isBlank()
                || request.player1() == null || request.opponentType() == null
                || (request.opponentType() == OpponentType.HUMAN && request.player2() == null)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solicitud de partida incompleta"));
        }
        var state = roomService.create(request.matchId(), request.player1(),
                request.player2(), request.opponentType());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("gameId", state.getGameId(), "status", state.getStatus()));
    }
}
