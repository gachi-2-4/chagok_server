package gachi.chagok_server.domain.chat.repository;

import gachi.chagok_server.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 최신 메시지부터 */
    List<ChatMessage> findByChatIdOrderByIdDesc(Long chatId, Pageable pageable);

    /** beforeMessageId보다 오래된 메시지를 최신부터 */
    List<ChatMessage> findByChatIdAndIdLessThanOrderByIdDesc(Long chatId, Long beforeMessageId, Pageable pageable);
}
