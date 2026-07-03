package com.puckzone.game.room;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;

/**
 * Snapshot del estado de partida en Redis, como JSON bajo {@code game:{id}}.
 * No se escribe en cada tick (60/s x 20 partidas comprometería la latencia):
 * el {@link GameRoomService} y el motor lo invocan en eventos clave
 * (creación, conexión, gol, fin). El TTL limpia partidas huérfanas.
 */
@Repository
public class GameStateRepository {

    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;

    public GameStateRepository(StringRedisTemplate redis, JsonMapper jsonMapper) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
    }

    public void save(GameState state) {
        redis.opsForValue().set(key(state.getGameId()), jsonMapper.writeValueAsString(state), TTL);
    }

    public Optional<GameState> find(String gameId) {
        return Optional.ofNullable(redis.opsForValue().get(key(gameId)))
                .map(json -> jsonMapper.readValue(json, GameState.class));
    }

    public void delete(String gameId) {
        redis.delete(key(gameId));
    }

    private String key(String gameId) {
        return "game:" + gameId;
    }
}
