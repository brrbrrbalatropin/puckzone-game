package com.puckzone.game.config;

import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadatos del API para springdoc. Documenta solo el REST (POST /games, que
 * consume matchmaking); el contrato WS/STOMP (SockJS /ws, /app/game/{id}/...,
 * /topic/game/{id}) está en el README — OpenAPI no cubre WebSockets.
 * El server relativo "/" hace que el Try it out apunte al origen desde donde
 * se cargó la spec (el gateway en Azure).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI puckzoneOpenApi() {
        return new OpenAPI()
                .servers(List.of(new Server().url("/")))
                .info(new Info()
                        .title("PuckZone Game API")
                        .version("v1")
                        .description("Creación de salas de partida (la invoca matchmaking). "
                                + "El juego en sí corre por WebSocket STOMP/SockJS en /ws "
                                + "(ver README para el contrato de mensajes)."))
                .components(new Components().addSecuritySchemes("bearer-jwt",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
