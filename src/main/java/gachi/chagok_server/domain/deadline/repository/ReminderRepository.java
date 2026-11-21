package gachi.chagok_server.domain.deadline.repository;

import gachi.chagok_server.domain.deadline.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    Optional<Reminder> findByDeadlineId(Long deadlineId);

    List<Reminder> findByDeadlineIdIn(Collection<Long> deadlineIds);

    @Query("select r from Reminder r join fetch r.deadline d join fetch d.document doc join fetch doc.box "
            + "where r.id = :id and d.user.id = :userId and d.deleted = false")
    Optional<Reminder> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    /** 보낼 때가 된 알림 */
    @Query("select r from Reminder r join fetch r.deadline d join fetch d.document doc join fetch doc.box join fetch d.user "
            + "where r.nextAlertAt <= :now and d.deleted = false")
    List<Reminder> findDue(@Param("now") Instant now);

    @Modifying
    @Query("delete from Reminder r where r.deadline.id in (select d.id from Deadline d where d.document.id = :documentId)")
    void deleteByDocumentId(@Param("documentId") Long documentId);
}
