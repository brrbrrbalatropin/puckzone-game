package com.puckzone.game.room;

import com.puckzone.game.security.JwtTokenParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Entrada HTTP del servicio. {@code POST /games} crea la sala (la invoca
 * matchmaking por la red interna; solo mira el código de estado, el
 * cuerpo con el gameId se devuelve para poder probar con curl).
 * {@code GET /api/game/active} atiende al frontend vía gateway — el path
 * lleva el prefijo público porque la ruta del gateway no hace rewrite.
 */
@RestController
public class GameRoomController {

    private final GameRoomService roomService;
    private final JwtTokenParser tokenParser;

    public GameRoomController(GameRoomService roomService, JwtTokenParser tokenParser) {
        this.roomService = roomService;
        this.tokenParser = tokenParser;
    }

    @PostMapping("/games")
    public ResponseEntity<?> createGame(@RequestBody CreateGameRequest request) {
        if (request.matchId() == null || request.matchId().isBlank()
                || request.player1() == null || request.opponentType() == null
                || (request.opponentType() == OpponentType.HUMAN && request.player2() == null)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Solicitud de partida incompleta"));
        }
        var state = roomService.create(request.matchId(), request.player1(),
                request.player2(), request.opponentType(), request.friendly());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("gameId", state.getGameId(), "status", state.getStatus()));
    }

    /**
     * ¿Tiene este usuario una partida viva a la cual volver? Para la
     * pantalla de reconexión del lobby (ej. cerró la pestaña a mitad de
     * partida). La identidad sale del Bearer token, validado aquí mismo:
     * 200 con el resumen si hay partida, 204 si no, 401 sin token válido.
     */
    @GetMapping("/api/game/active")
    public ResponseEntity<?> activeGame(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : null;
        var userId = tokenParser.userIdFrom(token);
        if (userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Token requerido o inválido"));
        }
        return roomService.activeGameOf(userId.get())
                .<ResponseEntity<?>>map(state -> ResponseEntity.ok(ActiveGameResponse.of(state, userId.get())))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
