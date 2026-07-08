package com.puckzone.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuración del reporte de resultados a puckzone-ranking, bajo
 * {@code puckzone.ranking-report}. En Azure la base-url se sobreescribe con
 * RANKING_BASE_URL (http://puckzone-ranking, HTTP plano interno del environment).
 */
@ConfigurationProperties(prefix = "puckzone.ranking-report")
public record RankingReportProperties(
        @DefaultValue("http://localhost:8084") String baseUrl,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout
) {
}
