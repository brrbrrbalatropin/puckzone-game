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
 *
 * <p>Guarda además el índice {@code active-game:{userId}} →
 * {@link ActiveGameRef}: como Redis es compartido entre los shards de
 * game, cualquier instancia puede decirle al lobby en qué shard vive la
 * partida a la cual el usuario puede volver.
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

    public void saveActiveRef(String userId, ActiveGameRef ref) {
        redis.opsForValue().set(activeKey(userId), jsonMapper.writeValueAsString(ref), TTL);
    }

    public Optional<ActiveGameRef> findActiveRef(String userId) {
        return Optional.ofNullable(redis.opsForValue().get(activeKey(userId)))
                .map(json -> jsonMapper.readValue(json, ActiveGameRef.class));
    }

    /**
     * Borra la referencia solo si aún apunta a esta partida: la limpieza
     * tardía de una sala vieja no debe tumbar la referencia de una
     * partida nueva del mismo usuario.
     */
    public void deleteActiveRef(String userId, String gameId) {
        Optional.ofNullable(redis.opsForValue().get(activeKey(userId)))
                .map(json -> jsonMapper.readValue(json, ActiveGameRef.class))
                .filter(ref -> ref.gameId().equals(gameId))
                .ifPresent(ref -> redis.delete(activeKey(userId)));
    }

    private String key(String gameId) {
        return "game:" + gameId;
    }

    private String activeKey(String userId) {
        return "active-game:" + userId;
    }
}
