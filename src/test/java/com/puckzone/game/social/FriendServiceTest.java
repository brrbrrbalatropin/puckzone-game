package com.puckzone.game.social;

import com.puckzone.game.social.FriendViews.Overview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas del sistema de amigos contra la BD real (H2): solicitud →
 * aceptación, la solicitud cruzada que se vuelve aceptación automática,
 * los rechazos con su status HTTP y que borrar la relación deja a la
 * pareja libre de reintentar.
 */
@DataJpaTest
@Import({FriendService.class, PresenceService.class})
class FriendServiceTest {

    private static final String ANA = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String BETO = "bbbbbbbb-0000-0000-0000-000000000002";

    @Autowired
    private FriendService service;
    @Autowired
    private PlayerDirectoryRepository directory;

    @BeforeEach
    void seedDirectory() {
        directory.save(PlayerDirectoryEntry.builder()
                .userId(ANA).username("ana").university("escuelaing").lastSeenAtEpochMs(1).build());
        directory.save(PlayerDirectoryEntry.builder()
                .userId(BETO).username("beto").university("unal").lastSeenAtEpochMs(2).build());
    }

    @Test
    void flujoCompleto_solicitudAceptadaLosVuelveAmigosParaAmbos() {
        var request = service.sendRequest(ANA, "Beto"); // sin distinguir caso
        assertThat(request.username()).isEqualTo("beto");

        Overview deBeto = service.overviewOf(BETO);
        assertThat(deBeto.incoming()).hasSize(1);
        assertThat(deBeto.incoming().getFirst().username()).isEqualTo("ana");

        var amigo = service.accept(BETO, request.friendshipId());
        assertThat(amigo.username()).isEqualTo("ana");

        assertThat(service.overviewOf(ANA).friends()).extracting("username").containsExactly("beto");
        assertThat(service.overviewOf(BETO).friends()).extracting("username").containsExactly("ana");
        assertThat(service.areFriends(ANA, BETO)).isTrue();
    }

    @Test
    void laSolicitudCruzadaSeConvierteEnAceptacionAutomatica() {
        service.sendRequest(ANA, "beto");
        service.sendRequest(BETO, "ana"); // en vez de fallar, acepta

        assertThat(service.areFriends(ANA, BETO)).isTrue();
        assertThat(service.overviewOf(ANA).outgoing()).isEmpty();
    }

    @Test
    void losErroresDeNegocioLlevanSuStatus() {
        assertThatThrownBy(() -> service.sendRequest(ANA, "nadie"))
                .isInstanceOfSatisfying(SocialException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.sendRequest(ANA, "ana"))
                .isInstanceOfSatisfying(SocialException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));

        var request = service.sendRequest(ANA, "beto");
        assertThatThrownBy(() -> service.sendRequest(ANA, "beto"))
                .isInstanceOfSatisfying(SocialException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));

        service.accept(BETO, request.friendshipId());
        assertThatThrownBy(() -> service.sendRequest(ANA, "beto"))
                .isInstanceOfSatisfying(SocialException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void soloElDestinatarioAceptaYSoloUnParticipanteBorra() {
        var request = service.sendRequest(ANA, "beto");

        assertThatThrownBy(() -> service.accept(ANA, request.friendshipId()))
                .isInstanceOfSatisfying(SocialException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.deleteRelation("otro-usuario", request.friendshipId()))
                .isInstanceOfSatisfying(SocialException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void borrarLaRelacionPermiteVolverAInvitar() {
        var request = service.sendRequest(ANA, "beto");
        service.deleteRelation(BETO, request.friendshipId()); // Beto rechaza

        assertThat(service.overviewOf(ANA).outgoing()).isEmpty();
        var second = service.sendRequest(BETO, "ana"); // y luego se arrepiente
        assertThat(second.username()).isEqualTo("ana");
    }

    @Test
    void elBuscadorExcluyeAlPropioUsuario() {
        assertThat(service.search(BETO, "an")).extracting("username").containsExactly("ana");
        assertThat(service.search(ANA, "an")).isEmpty(); // ana no se encuentra a sí misma
        assertThat(service.search(ANA, "  ")).isEmpty();
    }
}
