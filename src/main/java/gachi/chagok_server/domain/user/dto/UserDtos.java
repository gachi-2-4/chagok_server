package gachi.chagok_server.domain.user.dto;
import gachi.chagok_server.domain.user.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
public final class UserDtos {
 private UserDtos(){}
 public record Region(@NotBlank String sido,@NotBlank String sigungu){}
 public record Profile(@NotBlank String nationality, Region region,@NotBlank String housingType,@NotBlank String welfareType,Instant updatedAt){}
 public record ProfileRequest(@NotBlank String nationality,@NotNull @Valid Region region,@NotBlank String housingType,@NotBlank String welfareType,LocalDate birthDate){}
 public record ProfileResult(String nationality,Region region,String housingType,String welfareType,LocalDate birthDate,boolean onboardingCompleted,Instant updatedAt){}
 public record FontRequest(@NotNull FontSize fontSize){}
 public record FontResult(FontSize fontSize,Instant updatedAt){}
 public record ConsentRequest(Boolean termsOfService,Boolean privacyPolicy,Boolean guardianConsent,Boolean overseasTransfer,Boolean marketing){}
 public record ConsentValue(boolean agreed,String version,Instant updatedAt){}
 public record ConsentResult(Map<String,ConsentValue> consents,boolean consentRequired){}
 public record MyInfo(Long userId,String name,LocalDate birthDate,String phoneNumber,LoginType loginType,boolean onboardingCompleted,boolean consentRequired,Profile profile,FontSize fontSize,Map<String,Boolean> consents,boolean notificationEnabled,Instant createdAt){}
 public record InternalInfo(Long userId,LocalDate birthDate,boolean onboardingCompleted,boolean consentRequired,Profile profile,Map<String,Boolean> consents){}
}
