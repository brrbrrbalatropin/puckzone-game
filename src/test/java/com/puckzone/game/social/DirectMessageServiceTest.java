package com.puckzone.game.social;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas de los mensajes directos: solo entre amigos (en ambos sentidos:
 * escribir y leer), texto validado, cooldown por remitente e historial
 * cronológico acotado a la última página.
 */
@DataJpaTest
@Import({FriendService.class, PresenceService.class})
class DirectMessageServiceTest {

    private static final String ANA = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String BETO = "bbbbbbbb-0000-0000-0000-000000000002";
    private static final String CARLA = "cccccccc-0000-0000-0000-000000000003";

    /** Instancia fresca por test: el mapa de cooldown no debe cruzar tests. */
    private DirectMessageService service;
    @Autowired
    private FriendService friendService;
    @Autowired
    private PlayerDirectoryRepository directory;
    @Autowired
    private DirectMessageRepository messages;

    @BeforeEach
    void anaYBetoSonAmigos() {
        service = new DirectMessageService(messages, friendService);
        directory.save(PlayerDirectoryEntry.builder()
                .userId(ANA).username("ana").university("escuelaing").lastSeenAtEpochMs(1).build());
        directory.save(PlayerDirectoryEntry.builder()
                .userId(BETO).username("beto").university("unal").lastSeenAtEpochMs(2).build());
        var request = friendService.sendRequest(ANA, "beto");
        friendService.accept(BETO, request.friendshipId());
    }

    @Test
    void elMensajeEntreAmigosSePersisteYSaleEnElHistorialDeAmbos() {
        var view = service.send(ANA, BETO, "  ¿jugamos?  ");
        assertThat(view.content()).isEqualTo("¿jugamos?");
        assertThat(view.senderId()).isEqualTo(ANA);

        assertThat(messages.count()).isEqualTo(1);
        assertThat(service.history(ANA, BETO)).hasSize(1);
        assertThat(service.history(BETO, ANA)).hasSize(1);
    }

    @Test
    void losExtranosNoEscribenNiLeen() {
        assertThatThrownBy(() -> service.send(CARLA, ANA, "hola"))
                .isInstanceOfSatisfying(SocialException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.history(CARLA, ANA))
                .isInstanceOfSatisfying(SocialException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(messages.count()).isZero();
    }

    @Test
    void rechazaVacioMuyLargoOSinDestinatario() {
        assertThatThrownBy(() -> service.send(ANA, BETO, "   "))
                .isInstanceOfSatisfying(SocialException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.send(ANA, BETO, "x".repeat(201)))
                .isInstanceOfSatisfying(SocialException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.send(ANA, null, "hola"))
                .isInstanceOfSatisfying(SocialException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void elCooldownFrenaElSegundoMensajeInmediato() {
        service.send(ANA, BETO, "primero");
        assertThatThrownBy(() -> service.send(ANA, BETO, "segundo"))
                .isInstanceOfSatisfying(SocialException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        // El destinatario puede responder de una: el cooldown es por remitente.
        assertThat(service.send(BETO, ANA, "aquí estoy").content()).isEqualTo("aquí estoy");
    }

    @Test
    void elHistorialEsCronologicoYDevuelveSoloLaUltimaPagina() {
        String key = DirectMessage.conversationKeyOf(ANA, BETO);
        for (int i = 0; i < 60; i++) {
            messages.save(DirectMessage.builder().conversationKey(key)
                    .senderId(i % 2 == 0 ? ANA : BETO)
                    .recipientId(i % 2 == 0 ? BETO : ANA)
                    .content("msg-" + i).sentAtEpochMs(1000 + i).build());
        }

        var history = service.history(ANA, BETO);
        assertThat(history).hasSize(50);
        assertThat(history.getFirst().content()).isEqualTo("msg-10");
        assertThat(history.getLast().content()).isEqualTo("msg-59");
    }
}
