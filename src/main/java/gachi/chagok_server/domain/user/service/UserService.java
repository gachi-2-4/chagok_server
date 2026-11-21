package gachi.chagok_server.domain.user.service;
import gachi.chagok_server.domain.user.dto.UserDtos.*;
import gachi.chagok_server.domain.user.entity.*;
import gachi.chagok_server.domain.user.repository.*;
import gachi.chagok_server.domain.notification.repository.NotificationSettingsRepository;
import gachi.chagok_server.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class UserService {
 private final UserRepository users;
 private final UserProfileRepository profiles;
 private final ConsentHistoryRepository histories;
 private final NotificationSettingsRepository notificationSettings;
 @Value("${chagok.consents.version:2026-11-01}") private String consentVersion;
 private User user(Long id){return users.findById(id).filter(u->!u.isDeleted()).orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));}
 public MyInfo me(Long id){User u=user(id);return new MyInfo(u.getId(),u.getName(),u.getBirthDate(),u.getPhoneNumber(),u.getLoginType(),u.isOnboardingCompleted(),consentRequired(u),profile(id),u.getFontSize(),consentFlags(id),notificationSettings.findById(id).map(x->x.isDeadlineAlertEnabled()).orElse(true),u.getCreatedAt());}
 public InternalInfo internal(Long id){User u=user(id);return new InternalInfo(u.getId(),u.getBirthDate(),u.isOnboardingCompleted(),consentRequired(u),profile(id),consentFlags(id));}
 private Profile profile(Long id){return profiles.findById(id).map(p->new Profile(p.getNationality(),new Region(p.getSido(),p.getSigungu()),p.getHousingType(),p.getWelfareType(),p.getUpdatedAt())).orElse(null);}
 public Map<String,Boolean> consentFlags(Long id){Map<String,Boolean> m=new LinkedHashMap<>();for(ConsentType t:ConsentType.values())m.put(key(t),false);
 Map<String,Boolean> latest=new LinkedHashMap<>();for(ConsentHistory h:histories.findByUserIdOrderByUpdatedAtDescIdDesc(id))latest.putIfAbsent(key(h.getType()),h.isAgreed());m.putAll(latest);return m;}
 private String key(ConsentType t){return switch(t){case TERMS_OF_SERVICE->"termsOfService";case PRIVACY_POLICY->"privacyPolicy";case GUARDIAN_CONSENT->"guardianConsent";case OVERSEAS_TRANSFER->"overseasTransfer";case MARKETING->"marketing";};}
 public boolean consentRequired(User u){var m=consentFlags(u.getId()); boolean under14=u.getBirthDate()!=null&&u.getBirthDate().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(14));return !m.get("termsOfService")||!m.get("privacyPolicy")||(under14&&!m.get("guardianConsent"));}
 @Transactional public FontResult changeFont(Long id,FontRequest req){User u=user(id);u.changeFontSize(req.fontSize());return new FontResult(u.getFontSize(),u.getFontSizeUpdatedAt());}
 @Transactional public ProfileResult saveProfile(Long id,ProfileRequest req){User u=user(id);if(consentRequired(u))throw new BusinessException(ErrorCode.INVALID_INPUT);if(!List.of("KR","FOREIGN").contains(req.nationality())||!List.of("MONTHLY_RENT","JEONSE","OWNED","DORMITORY","WITH_PARENTS").contains(req.housingType())||!List.of("BASIC_LIVELIHOOD","NEAR_POVERTY","OTHER","NONE").contains(req.welfareType()))throw new BusinessException(ErrorCode.INVALID_INPUT);
 if(req.birthDate()!=null){if(u.getBirthDate()!=null&&!u.getBirthDate().equals(req.birthDate()))throw new BusinessException(ErrorCode.INVALID_INPUT);u.setBirthDateOnce(req.birthDate());}if(u.getBirthDate()==null)throw new BusinessException(ErrorCode.INVALID_INPUT);
 if(u.getBirthDate().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(14))&&!consentFlags(id).get("guardianConsent"))throw new BusinessException(ErrorCode.INVALID_INPUT);
 UserProfile p=profiles.findById(id).orElseGet(()->new UserProfile(u));p.update(req.nationality(),req.region().sido(),req.region().sigungu(),req.housingType(),req.welfareType());profiles.save(p);u.markOnboardingCompleted();return new ProfileResult(p.getNationality(),new Region(p.getSido(),p.getSigungu()),p.getHousingType(),p.getWelfareType(),u.getBirthDate(),true,p.getUpdatedAt());}
 @Transactional public ConsentResult consent(Long id,ConsentRequest req){User u=user(id);add(u,ConsentType.TERMS_OF_SERVICE,req.termsOfService(),true);add(u,ConsentType.PRIVACY_POLICY,req.privacyPolicy(),true);add(u,ConsentType.GUARDIAN_CONSENT,req.guardianConsent(),false);add(u,ConsentType.OVERSEAS_TRANSFER,req.overseasTransfer(),false);add(u,ConsentType.MARKETING,req.marketing(),false);return consentDetail(u);}
 private void add(User u,ConsentType type,Boolean agreed,boolean required){if(agreed==null)return;if(required&&!agreed)throw new BusinessException(ErrorCode.INVALID_INPUT);histories.save(new ConsentHistory(u,type,agreed,consentVersion));}
 private ConsentResult consentDetail(User u){Map<String,ConsentValue> m=new LinkedHashMap<>();for(ConsentType t:ConsentType.values())m.put(key(t),new ConsentValue(false,null,null));for(ConsentHistory h:histories.findByUserIdOrderByUpdatedAtDescIdDesc(u.getId())){String k=key(h.getType());if(m.get(k).updatedAt()==null)m.put(k,new ConsentValue(h.isAgreed(),h.getVersion(),h.getUpdatedAt()));}return new ConsentResult(m,consentRequired(u));}
}
