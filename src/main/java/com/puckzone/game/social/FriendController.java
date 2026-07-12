package com.puckzone.game.social;

import com.puckzone.game.security.JwtTokenParser;
import com.puckzone.game.social.FriendViews.FriendView;
import com.puckzone.game.social.FriendViews.Overview;
import com.puckzone.game.social.FriendViews.RequestView;
import com.puckzone.game.social.FriendViews.SearchView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST del sistema de amigos, consumido por el frontend vía gateway. El
 * path lleva el prefijo público completo porque la ruta game del gateway
 * no hace rewrite (igual que /api/game/active). La identidad sale del
 * Bearer token validado aquí mismo con el mismo parser del handshake WS.
 */
@RestController
@RequestMapping("/api/game/friends")
public class FriendController {

    /** Cuerpo de la solicitud de amistad: a quién, por username. */
    public record AddFriendRequest(String username) {
    }

    private final FriendService friends;
    private final DirectMessageService directMessages;
    private final JwtTokenParser tokenParser;

    public FriendController(FriendService friends, DirectMessageService directMessages,
                            JwtTokenParser tokenParser) {
        this.friends = friends;
        this.directMessages = directMessages;
        this.tokenParser = tokenParser;
    }

    /** Panel social completo: amigos con presencia + solicitudes en ambos sentidos. */
    @GetMapping
    public Overview overview(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return friends.overviewOf(requireUser(authorization));
    }

    @PostMapping("/requests")
    public ResponseEntity<RequestView> sendRequest(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AddFriendRequest request) {
        RequestView view = friends.sendRequest(requireUser(authorization),
                request == null ? null : request.username());
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @PostMapping("/requests/{friendshipId}/accept")
    public FriendView accept(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long friendshipId) {
        return friends.accept(requireUser(authorization), friendshipId);
    }

    /** Rechazar, cancelar o eliminar amigo: borrar la relación, da igual el estado. */
    @DeleteMapping("/{friendshipId}")
    public ResponseEntity<Void> deleteRelation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long friendshipId) {
        friends.deleteRelation(requireUser(authorization), friendshipId);
        return ResponseEntity.noContent().build();
    }

    /** Conversación con un amigo (últimos 50, cronológico) al abrir el chat. */
    @GetMapping("/{friendUserId}/messages")
    public List<DirectMessageService.MessageView> messages(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String friendUserId) {
        return directMessages.history(requireUser(authorization), friendUserId);
    }

    @GetMapping("/search")
    public List<SearchView> search(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(name = "q", required = false) String query) {
        return friends.search(requireUser(authorization), query);
    }

    @ExceptionHandler(SocialException.class)
    public ResponseEntity<Map<String, String>> onSocialError(SocialException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    private String requireUser(String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : null;
        return tokenParser.userIdFrom(token)
                .orElseThrow(() -> new SocialException(HttpStatus.UNAUTHORIZED,
                        "Token requerido o inválido"));
    }
}
