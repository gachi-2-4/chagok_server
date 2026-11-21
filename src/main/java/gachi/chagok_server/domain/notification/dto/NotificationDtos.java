package gachi.chagok_server.domain.notification.dto;
import gachi.chagok_server.domain.notification.entity.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class NotificationDtos {
 private NotificationDtos(){}
 public enum Filter { ALL, UNREAD, COMPLETED }
 public record Document(Long documentId,String title,String boxName){}
 public record Deadline(Long deadlineId,String type,String label,LocalDate date,Long amount,Long dDay){}
 public record Comparison(Long comparisonId){}
 public record Item(Long notificationId,NotificationType type,String title,String message,Deadline deadline,Long reminderId,Integer advanceDay,Document document,Comparison comparison,boolean isRead,Instant readAt,boolean isCompleted,Instant completedAt,Instant sentAt) {
  public static Item from(Notification n){return new Item(n.getId(),n.getType(),n.getTitle(),n.getMessage(),n.getDeadlineId()==null?null:new Deadline(n.getDeadlineId(),n.getDeadlineType(),n.getDeadlineLabel(),n.getDeadlineDate(),n.getDeadlineAmount(),n.getDeadlineDate()==null?null:java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(ZoneId.of("Asia/Seoul")),n.getDeadlineDate())),n.getReminderId(),n.getAdvanceDay(),n.getDocumentId()==null?null:new Document(n.getDocumentId(),n.getDocumentTitle(),n.getBoxName()),n.getComparisonId()==null?null:new Comparison(n.getComparisonId()),n.getReadAt()!=null,n.getReadAt(),n.getCompletedAt()!=null,n.getCompletedAt(),n.getSentAt());}
 }
 public record ListResult(long unreadCount,List<Item> content,int page,int size,long totalElements,boolean hasNext){}
 public record UpdateRequest(Boolean isRead,Boolean isCompleted){}
 public record UpdateResult(Long notificationId,boolean isRead,Instant readAt,boolean isCompleted,Instant completedAt,long unreadCount){}
 public record AllReadResult(int updatedCount,long unreadCount){}
 public record SettingsRequest(Boolean deadlineAlertEnabled,String defaultAlertTime){}
 public record SettingsResult(boolean deadlineAlertEnabled,boolean marketingAgreed,String defaultAlertTime){}
 public record PushRequest(@NotBlank String fcmToken,@NotBlank String platform,@NotNull Boolean permissionGranted,String appVersion){}
}
