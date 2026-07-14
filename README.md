# puckzone-game

Real-time gameplay microservice for **PuckZone**, a real-time multiplayer air hockey web
platform for Colombian university students. This is the heart of the platform: it runs the
authoritative **2D physics engine**, streams game state to both players over WebSockets, hosts
the **adaptive bot**, the **6 powers**, and all the social features (friends, direct messages,
lobby/university chat, in-game emotes and voice signalling). It is one of six independent
microservices.

> PuckZone is an individual project for the Software Architectures course (ARSW) at
> Escuela Colombiana de Ingeniería Julio Garavito, term 2026-i.

| Service | Role | Local port |
|---|---|---|
| puckzone-gateway | Single entry point, routing, JWT validation, CORS, rate limiting | 8080 |
| puckzone-auth | Registration, login, JWT issuing | 8081 |
| puckzone-matchmaking | Rating-based player matching, bot fallback | 8082 |
| **puckzone-game** (this repo) | Real-time gameplay, physics, bot, social features | **8083** |
| puckzone-ranking | ELO ratings and leaderboards | 8084 |
| puckzone-frontend | React + Vite client | 5173 |

## What this service does

- **Hosts the match rooms.** `matchmaking` calls `POST /games` once two players are paired
  (or one player accepts the bot); this service creates the room and drives it.
- **Runs the physics.** A single server-side loop simulates the board at **60 ticks/second**
  (paddle–puck collisions, wall bounces, goals) and is the sole authority — clients only send
  their mouse input, never state.
- **Streams state over STOMP/SockJS.** Each player subscribes to `/topic/game/{id}` and
  receives the full `GameState` at 60 Hz; the client renders it on a canvas.
- **Plays as a bot** at 9 difficulty levels interpolated from ELO (level 4 is the classic
  "follow the puck" bot), so a solo player always has an opponent.
- **Implements the 6 powers** as board pickups with immediate effect.
- **Handles disconnections and surrender**: a 30 s grace pause on drop (reconnect = re-join),
  forfeit on abandonment, and an explicit *surrender* action.
- **Reports the result** to `puckzone-ranking` when a match finishes.
- **Runs the social layer**: friends + presence, persistent direct messages, global and
  per-university lobby chat, in-game emotes, and WebRTC voice signalling relay.

### Where it sits in the architecture

```
puckzone-matchmaking ──POST /games──▶  ┌───────────────────┐  ──POST /api/ranking/match──▶ puckzone-ranking
   (two players paired)                │   puckzone-game   │        (when a match ends)
                                       │  physics 60Hz      │
puckzone-frontend ──WS /ws (STOMP)────▶│  authoritative     │◀──── Redis (state snapshots)
   (via gateway, per player)           └───────────────────┘◀──── PostgreSQL game_db (friends, DMs)
```

The gateway proxies the WebSocket upgrade to `/ws` and forwards this service's REST endpoints.

## Gameplay rules

- Matches are played **to 7 goals** (no draws). Board is **800×500**, goal width 200.
- **6 powers** (`puckzone.game.power.*`), picked up by touching the pickup with your paddle:

  | Power | Effect |
  |---|---|
  | `OBSTACLE` | Solid circle that bounces the puck, anchored where the pickup was |
  | `FAST_ZONE` | Zone that speeds the puck up while inside it |
  | `SLOW_ZONE` | Zone that slows the puck down (defensive, near your own goal) |
  | `GHOST_PUCK` | Puck turns invisible until its next bounce |
  | `SHIELD` | The collector's paddle doubles its radius for a while |
  | `CHAOS` | The next paddle hit launches the puck at double speed |

- **Adaptive bot** (`puckzone.game.bot.*`): three anchors — level 1 (120 px/s, slow reaction,
  noisy aim), level 4 (220 px/s, the classic bot), level 9 (550 px/s, near-perfect) — and every
  other level is interpolated. The level is chosen from the player's ELO (`player1Rating` in the
  `POST /games` payload); when no rating arrives it defaults to level 4 (ELO 1200).
- **Friendly rooms** (`friendly: true`) play identically but do **not** move ELO or win/loss when
  reported to ranking.
- A goal triggers an announcement pause before the serve; a puck trapped against a corner escapes
  perpendicular after repeated identical-normal contacts.

## REST API

| Method | Path | Description |
|---|---|---|
| `POST` | `/games` | Create a room (called internally by `matchmaking`) |
| `GET` | `/api/game/active` | Does the caller have a live match to rejoin? (lobby reconnection) |
| `GET` | `/api/game/friends` | Social overview: friends + presence + pending requests |
| `POST` | `/api/game/friends/requests` | Send a friend request (by username) |
| `POST` | `/api/game/friends/requests/{id}/accept` | Accept a request |
| `DELETE` | `/api/game/friends/{id}` | Reject / cancel / unfriend (delete the relation) |
| `GET` | `/api/game/friends/{friendUserId}/messages` | Last 50 DMs with a friend |
| `GET` | `/api/game/friends/search?q=` | Search players by username |
| `GET` | `/actuator/health` | Liveness/readiness probe |

> The `/api/game/**` paths carry the full public prefix on purpose: the gateway's game route
> does **not** rewrite them. Identity comes from the `Authorization: Bearer <jwt>` header, which
> this service validates locally with the shared secret.

### `POST /games` — request body

```json
{
  "matchId":       "unique id (idempotency key at ranking)",
  "player1":       { "userId": "uuid", "username": "…", "university": "eci" },
  "player2":       { "userId": "uuid", "username": "…", "university": "unal" },
  "opponentType":  "HUMAN",
  "friendly":      false,
  "player1Rating": 1200
}
```

`player2` is `null` when `opponentType` is `BOT`. Responds `201 Created` with
`{ "gameId": "…", "status": "WAITING" }`.

## Real-time contract (STOMP over SockJS)

Clients connect to **`/ws?token=<jwt>`** (SockJS can't send an `Authorization` header, so the
token rides as a query param and is validated at the handshake). Identity is taken from the JWT
`Principal`, **not** from message payloads — a client can never act on another player's behalf.

**Client → server** (`/app/**`):

| Destination | Payload | Purpose |
|---|---|---|
| `/app/game/{id}/join` | — | Announce ready / (re)join the room |
| `/app/game/{id}/paddle` | `{ x, y }` | Mouse input (clamped to your half) |
| `/app/game/{id}/surrender` | — | Concede the match (confirmed client-side) |
| `/app/game/{id}/emote` | `{ emote }` | Emote (`THUMBS_UP\|LAUGH\|WOW\|CRY\|ANGRY\|GG`, 1 s cooldown) |
| `/app/game/{id}/voice` | WebRTC signal | Voice signalling, relayed to the opponent only |
| `/app/dm` | `{ toUserId, text }` | Send a direct message |
| `/app/lobby/chat` | `{ text }` | Global lobby chat |
| `/app/lobby/chat/university` | `{ text }` | University-only chat |

**Server → client**:

| Destination | Content |
|---|---|
| `/topic/game/{id}` | Full `GameState` at 60 Hz (positions, score, powers, status, `winnerId`) |
| `/topic/game/{id}/emotes` | Emote broadcasts |
| `/topic/lobby/chat` | Global chat broadcasts |
| `/topic/lobby/chat/university/{univ}` | Per-university chat (subscription guarded by JWT university) |
| `/user/queue/dm` | Direct messages (delivered to both participants) |
| `/user/queue/voice` | Relayed WebRTC signals |

## Tech stack

- Java 21 · Spring Boot 4.1 (Web MVC, WebSocket/STOMP, Data JPA, Data Redis, Actuator)
- **Redis** — game-state snapshots on key events
- **PostgreSQL 17** (`game_db`) — friends and direct messages (Hibernate-managed schema;
  H2 stands in for Postgres in tests)
- jjwt — local JWT validation at the WS handshake and on REST endpoints
- springdoc-openapi (Swagger) — documents the REST surface; the WS/STOMP contract lives here
- Maven · Docker multi-stage build · deployed to **Azure Container Apps**

### Project layout (package by feature)

```
com.puckzone.game
├── room/       Room lifecycle: GameState, GameRoomService, GameEndService, POST /games, /api/game/active
├── physics/    GameLoop (60 Hz), PhysicsEngine, TickOutcome
├── power/      The 6 powers: PowerType, PowerPickup, PowerManager, ActiveEffect
├── bot/        Adaptive AI: BotBrain, BotProfile, PuckPredictor
├── websocket/  STOMP controllers: game socket, chat (lobby/university), emotes, voice, disconnect listener
├── social/     Friends + presence + persistent DMs (JPA against game_db)
├── security/   JWT handshake handler/interceptor, token parser, StompPrincipal
├── client/     RankingClient — reports the finished match to puckzone-ranking
└── config/     WebSocketConfig + typed properties (game, bot, power, jwt, ranking-report)
```

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis for state snapshots |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/game_db` | JDBC URL (add `sslmode=require` in Azure) |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | `postgres` / `postgres` | DB credentials |
| `RANKING_BASE_URL` | `http://localhost:8084` | Where to report finished matches |
| `PUCKZONE_JWT_SECRET` | dev-only default | HMAC secret shared with the gateway. **Override in production.** |
| `PUCKZONE_WEBSOCKET_ALLOWEDORIGINS` | `localhost:5173,localhost:8080` | Allowed `Origin`s at the WS handshake |

Gameplay, bot and power tuning live under `puckzone.game.*` in
`src/main/resources/application.yaml`. The server listens on port **8083**.

## Running it locally

Requirements: **JDK 21** (Lombok breaks on newer JDKs — the machine default JDK 25 won't work),
plus a Redis and a PostgreSQL `game_db` reachable at the addresses above.

```bash
./mvnw spring-boot:run        # needs JDK 21
```

Then try it:

```bash
curl http://localhost:8083/actuator/health

curl -X POST http://localhost:8083/games \
  -H "Content-Type: application/json" \
  -d '{"matchId":"m1","player1":{"userId":"u1","username":"ana","university":"eci"},
       "opponentType":"BOT","friendly":false,"player1Rating":1200}'
```

To exercise the full loop, open the frontend and let it connect through the gateway to
`/ws?token=<jwt>`.

## Building the Docker image

```bash
docker build -t puckzone-game .
docker run -p 8083:8083 \
  -e REDIS_HOST=host.docker.internal \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/game_db \
  puckzone-game
```

The Dockerfile is a two-stage build (Maven + JDK 21 → JRE 21 runtime), so no local toolchain is
needed to build the image.

## Deployment (Terraform + CI/CD)

This repo owns the **shared** infrastructure in `infra/base/` (resource group, Log Analytics +
App Insights, the Container Apps environment, Redis, and the shared PostgreSQL server with
`auth_db` / `ranking_db` / `game_db`), plus its own Container App in `infra/app/`. Every other
service copies the `infra/app/` template and reads `base.tfstate` via `terraform_remote_state`.
See `infra/README.md` for the full setup.

The WebSocket path needs two Azure-specific settings that took a while to find: the ingress
`transport` must be **`http`** (not `auto`, which breaks the WS upgrade through the gateway), and
the SockJS endpoint sets `suppressCors(true)` so it doesn't duplicate the gateway's CORS headers.
Replicas are pinned to **min = max = 1** because rooms live in memory — scaling is the open
architectural work (availability/scalability is the current course focus).
