package com.puckzone.game.social;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio del paquete social con el status HTTP que le
 * corresponde; el controller lo traduce al {@code {"error": "..."}} que
 * usa el resto de la plataforma.
 */
public class SocialException extends RuntimeException {

    private final HttpStatus status;

    public SocialException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
