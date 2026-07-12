package com.puckzone.game.social;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consultas del paquete social contra H2: la relación de pareja se
 * encuentra en ambas direcciones, el historial de DMs sale por la clave
 * canónica de conversación y el directorio busca por username sin caso.
 */
@DataJpaTest
class SocialRepositoriesTest {

    private static final String ANA = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String BETO = "bbbbbbbb-0000-0000-0000-000000000002";
    private static final String CARLA = "cccccccc-0000-0000-0000-000000000003";

    @Autowired
    private FriendshipRepository friendships;
    @Autowired
    private DirectMessageRepository messages;
    @Autowired
    private PlayerDirectoryRepository directory;

    @Test
    void laRelacionDeParejaSeEncuentraEnAmbasDirecciones() {
        friendships.save(Friendship.builder()
                .requesterId(ANA).addresseeId(BETO)
                .status(FriendshipStatus.PENDING).createdAtEpochMs(1000).build());

        assertThat(friendships.findBetween(ANA, BETO)).isPresent();
        assertThat(friendships.findBetween(BETO, ANA)).isPresent();
        assertThat(friendships.findBetween(ANA, CARLA)).isEmpty();
    }

    @Test
    void lasAceptadasDelUsuarioSalenSinImportarQuienInvito() {
        friendships.save(Friendship.builder()
                .requesterId(ANA).addresseeId(BETO)
                .status(FriendshipStatus.ACCEPTED).createdAtEpochMs(1000).build());
        friendships.save(Friendship.builder()
                .requesterId(CARLA).addresseeId(ANA)
                .status(FriendshipStatus.ACCEPTED).createdAtEpochMs(2000).build());
        friendships.save(Friendship.builder()
                .requesterId(ANA).addresseeId("dddddddd-0000-0000-0000-000000000004")
                .status(FriendshipStatus.PENDING).createdAtEpochMs(3000).build());

        List<Friendship> deAna = friendships.findAcceptedOf(ANA);
        assertThat(deAna).hasSize(2);
        assertThat(friendships.findByAddresseeIdAndStatus(ANA, FriendshipStatus.PENDING)).isEmpty();
        assertThat(friendships.findByRequesterIdAndStatus(ANA, FriendshipStatus.PENDING)).hasSize(1);
    }

    @Test
    void elHistorialDeUnaParejaSaleConLaClaveCanonica() {
        String key = DirectMessage.conversationKeyOf(BETO, ANA);
        // La clave es la misma sin importar el orden de los argumentos.
        assertThat(key).isEqualTo(DirectMessage.conversationKeyOf(ANA, BETO));

        messages.save(DirectMessage.builder().conversationKey(key)
                .senderId(ANA).recipientId(BETO).content("hola").sentAtEpochMs(1000).build());
        messages.save(DirectMessage.builder().conversationKey(key)
                .senderId(BETO).recipientId(ANA).content("¿jugamos?").sentAtEpochMs(2000).build());
        messages.save(DirectMessage.builder()
                .conversationKey(DirectMessage.conversationKeyOf(ANA, CARLA))
                .senderId(CARLA).recipientId(ANA).content("otra charla").sentAtEpochMs(1500).build());

        List<DirectMessage> historial = messages.findTop50ByConversationKeyOrderBySentAtEpochMsDesc(key);
        assertThat(historial).hasSize(2);
        assertThat(historial.getFirst().getContent()).isEqualTo("¿jugamos?");
    }

    @Test
    void elDirectorioBuscaPorUsernameSinDistinguirCaso() {
        directory.save(PlayerDirectoryEntry.builder()
                .userId(ANA).username("AnaGamer").university("escuelaing")
                .lastSeenAtEpochMs(1000).build());

        assertThat(directory.findByUsernameIgnoreCase("anagamer")).isPresent();
        assertThat(directory.findTop10ByUsernameStartingWithIgnoreCaseOrderByUsername("ana"))
                .extracting(PlayerDirectoryEntry::getUsername)
                .containsExactly("AnaGamer");
    }
}
