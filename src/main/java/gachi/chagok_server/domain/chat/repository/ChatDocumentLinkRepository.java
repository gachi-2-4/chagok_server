package gachi.chagok_server.domain.chat.repository;

import gachi.chagok_server.domain.chat.entity.ChatDocumentLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatDocumentLinkRepository extends JpaRepository<ChatDocumentLink, Long> {

    List<ChatDocumentLink> findByChatIdOrderByIdAsc(Long chatId);

    @Modifying(flushAutomatically = true)
    @Query("delete from ChatDocumentLink l where l.chat.id = :chatId")
    void deleteByChatId(@Param("chatId") Long chatId);
}
