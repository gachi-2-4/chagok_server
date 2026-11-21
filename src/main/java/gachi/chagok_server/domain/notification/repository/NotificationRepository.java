package gachi.chagok_server.domain.notification.repository;
import gachi.chagok_server.domain.notification.entity.Notification;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
public interface NotificationRepository extends JpaRepository<Notification,Long>{
 Optional<Notification> findByIdAndUserId(Long id,Long userId);
 Page<Notification> findByUserId(Long userId,Pageable pageable);
 Page<Notification> findByUserIdAndReadAtIsNull(Long userId,Pageable pageable);
 Page<Notification> findByUserIdAndCompletedAtIsNotNull(Long userId,Pageable pageable);
 long countByUserIdAndReadAtIsNull(Long userId);
 java.util.List<Notification> findTop3ByUserIdOrderBySentAtDesc(Long userId);
 @Modifying @Query("update Notification n set n.readAt=:now where n.user.id=:userId and n.readAt is null") int readAll(@Param("userId")Long userId,@Param("now")Instant now);
 long deleteBySentAtBefore(Instant cutoff);
 /** 알림에서 완료로 표시한 기한 회차 (기한 목록의 isCompleted 계산용) */
 @Query("select n.deadlineId as deadlineId, n.deadlineDate as deadlineDate from Notification n where n.user.id=:userId and n.completedAt is not null and n.deadlineId is not null and n.deadlineDate is not null")
 java.util.List<CompletedDeadline> findCompletedDeadlines(@Param("userId")Long userId);
 /** 서류를 지우면 이미 보낸 알림은 남기고 연결된 서류·기한 정보만 비운다. */
 @Modifying @Query("update Notification n set n.documentId=null,n.documentTitle=null,n.boxName=null,n.deadlineId=null,n.deadlineType=null,n.deadlineLabel=null,n.deadlineDate=null,n.deadlineAmount=null,n.reminderId=null,n.advanceDay=null where n.documentId=:documentId")
 int detachDocument(@Param("documentId")Long documentId);
 /** 비교 결과를 지우면 그 비교 완료 알림은 남기고 비교 연결만 끊는다. */
 @Modifying @Query("update Notification n set n.comparisonId=null where n.comparisonId in :ids") int detachComparisons(@Param("ids")java.util.List<Long> ids);
 interface CompletedDeadline { Long getDeadlineId(); java.time.LocalDate getDeadlineDate(); }
}
