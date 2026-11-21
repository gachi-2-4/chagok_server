package gachi.chagok_server.domain.notification.service;
import gachi.chagok_server.domain.notification.dto.NotificationDtos.*;
import gachi.chagok_server.domain.notification.entity.*;
import gachi.chagok_server.domain.notification.repository.*;
import gachi.chagok_server.domain.user.entity.*;
import gachi.chagok_server.domain.user.repository.*;
import gachi.chagok_server.domain.user.service.UserService;
import gachi.chagok_server.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class NotificationService {
 private final NotificationRepository notifications;
 private final NotificationSettingsRepository settings;
 private final PushTokenRepository pushTokens;
 private final UserRepository users;
 private final UserService userService;
 private User user(Long id){return users.findById(id).filter(u->!u.isDeleted()).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
 private Notification owned(Long id,Long userId){return notifications.findByIdAndUserId(id,userId).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
 public ListResult list(Long uid,Filter filter,int page,int size){user(uid);if(page<0||size<1||size>100)throw new BusinessException(ErrorCode.INVALID_INPUT);Pageable pageable=PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"sentAt"));Page<Notification> data=switch(filter){case ALL->notifications.findByUserId(uid,pageable);case UNREAD->notifications.findByUserIdAndReadAtIsNull(uid,pageable);case COMPLETED->notifications.findByUserIdAndCompletedAtIsNotNull(uid,pageable);};return new ListResult(notifications.countByUserIdAndReadAtIsNull(uid),data.map(Item::from).getContent(),page,size,data.getTotalElements(),data.hasNext());}
 public Item detail(Long uid,Long id){user(uid);return Item.from(owned(id,uid));}
 @Transactional public UpdateResult update(Long uid,Long id,UpdateRequest r){user(uid);Notification n=owned(id,uid);if(Boolean.FALSE.equals(r.isRead()))throw new BusinessException(ErrorCode.INVALID_INPUT);if(Boolean.TRUE.equals(r.isRead()))n.read();if(r.isCompleted()!=null){if(n.getType()!=NotificationType.DEADLINE_DUE)throw new BusinessException(ErrorCode.INVALID_INPUT);n.completed(r.isCompleted());}return new UpdateResult(n.getId(),n.getReadAt()!=null,n.getReadAt(),n.getCompletedAt()!=null,n.getCompletedAt(),notifications.countByUserIdAndReadAtIsNull(uid));}
 @Transactional public AllReadResult readAll(Long uid){user(uid);int count=notifications.readAll(uid,Instant.now());return new AllReadResult(count,0);}
 private NotificationSettings getOrNew(Long uid){User u=user(uid);return settings.findById(uid).orElseGet(()->new NotificationSettings(u));}
 public SettingsResult settings(Long uid){NotificationSettings s=getOrNew(uid);return new SettingsResult(s.isDeadlineAlertEnabled(),userService.consentFlags(uid).get("marketing"),s.getDefaultAlertTime().toString());}
 @Transactional public SettingsResult changeSettings(Long uid,SettingsRequest req){NotificationSettings s=getOrNew(uid);LocalTime time=null;if(req.defaultAlertTime()!=null){try{time=LocalTime.parse(req.defaultAlertTime());}catch(Exception ex){throw new BusinessException(ErrorCode.INVALID_INPUT);}if(time.getMinute()%10!=0||time.getSecond()!=0)throw new BusinessException(ErrorCode.INVALID_INPUT);}s.change(req.deadlineAlertEnabled(),time);settings.save(s);return settings(uid);}
 @Transactional public void pushToken(Long uid,String deviceId,PushRequest req){User u=user(uid);try{UUID.fromString(deviceId);}catch(Exception e){throw new BusinessException(ErrorCode.INVALID_INPUT);}if(!List.of("ANDROID","IOS").contains(req.platform()))throw new BusinessException(ErrorCode.INVALID_INPUT);PushToken token=pushTokens.findById(deviceId).orElseGet(()->new PushToken(deviceId,u));token.update(u,req.fcmToken(),req.platform(),req.permissionGranted(),req.appVersion());pushTokens.save(token);}
 @Transactional public Long create(Long uid,NotificationType type,String title,String message){return notifications.save(new Notification(user(uid),type,title,message)).getId();}
}
