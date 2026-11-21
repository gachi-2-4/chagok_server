package gachi.chagok_server.domain.chat.repository;

import gachi.chagok_server.domain.chat.entity.ChatBoxLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatBoxLinkRepository extends JpaRepository<ChatBoxLink, Long> {

    List<ChatBoxLink> findByChatIdOrderByIdAsc(Long chatId);

    @Modifying(flushAutomatically = true)
    @Query("delete from ChatBoxLink l where l.chat.id = :chatId")
    void deleteByChatId(@Param("chatId") Long chatId);

    /** 박스를 지우면 대화에서 그 박스 연결도 끊는다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from ChatBoxLink l where l.boxId = :boxId")
    void deleteByBoxId(@Param("boxId") Long boxId);
}
