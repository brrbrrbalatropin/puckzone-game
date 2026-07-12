package com.puckzone.game.social;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, Long> {

    /**
     * Última página del historial de una pareja (llega más reciente primero;
     * el servicio la invierte para entregarla en orden cronológico).
     */
    List<DirectMessage> findTop50ByConversationKeyOrderBySentAtEpochMsDesc(String conversationKey);
}
