package gachi.chagok_server.domain.notification.entity;
import gachi.chagok_server.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
@Entity @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
@Table(name="notifications",indexes=@Index(name="idx_notifications_user_sent",columnList="user_id,sent_at"))
public class Notification {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id") private User user;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private NotificationType type;
 @Column(nullable=false,length=160) private String title;
 @Column(nullable=false,length=2000) private String message;
 private Long documentId;private String documentTitle;private String boxName;
 private Long deadlineId;private String deadlineType;private String deadlineLabel;private LocalDate deadlineDate;private Long deadlineAmount;
 private Long reminderId;private Integer advanceDay;private Long comparisonId;
 @Column(name="sent_at",nullable=false) private Instant sentAt;
 private Instant readAt;private Instant completedAt;
 public Notification(User user,NotificationType type,String title,String message){this.user=user;this.type=type;this.title=title;this.message=message;this.sentAt=Instant.now();if(type==NotificationType.COMPARISON_COMPLETED)this.readAt=this.sentAt;}
 public void withDocument(Long id,String title,String box){documentId=id;documentTitle=title;boxName=box;}
 public void withDeadline(Long id,String type,String label,LocalDate date,Long amount,Long reminder,Integer advance){deadlineId=id;deadlineType=type;deadlineLabel=label;deadlineDate=date;deadlineAmount=amount;reminderId=reminder;advanceDay=advance;}
 public void withComparison(Long id){comparisonId=id;}
 public void read(){if(readAt==null)readAt=Instant.now();}
 public void completed(boolean value){if(type!=NotificationType.DEADLINE_DUE)throw new IllegalArgumentException("NOT_COMPLETABLE");completedAt=value?Instant.now():null;if(value)read();}
}
