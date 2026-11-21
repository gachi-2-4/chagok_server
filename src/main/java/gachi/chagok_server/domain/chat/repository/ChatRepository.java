package gachi.chagok_server.domain.chat.repository;

import gachi.chagok_server.domain.chat.entity.Chat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    Optional<Chat> findByIdAndUserId(Long id, Long userId);

    /** 질문을 하나도 하지 않은 대화(title=null)는 빼고, 마지막 메시지 최신순으로 준다. */
    @Query(value = "select c from Chat c where c.user.id = :userId and c.title is not null order by c.lastMessageAt desc, c.id desc",
            countQuery = "select count(c) from Chat c where c.user.id = :userId and c.title is not null")
    Page<Chat> findListed(@Param("userId") Long userId, Pageable pageable);

    /** 대화 제목과 메시지 내용에서 찾는다. pattern은 LIKE 패턴(특수문자는 \ 로 막음)이다. */
    @Query(value = "select c from Chat c where c.user.id = :userId and c.title is not null "
            + "and (c.title like :pattern escape '\\' or exists "
            + "(select 1 from ChatMessage m where m.chat = c and m.content like :pattern escape '\\')) "
            + "order by c.lastMessageAt desc, c.id desc",
            countQuery = "select count(c) from Chat c where c.user.id = :userId and c.title is not null "
                    + "and (c.title like :pattern escape '\\' or exists "
                    + "(select 1 from ChatMessage m where m.chat = c and m.content like :pattern escape '\\'))")
    Page<Chat> searchListed(@Param("userId") Long userId, @Param("pattern") String pattern, Pageable pageable);
}
