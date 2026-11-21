package gachi.chagok_server.domain.auth.service;
import gachi.chagok_server.domain.auth.oauth.*;
import gachi.chagok_server.domain.auth.dto.response.*;
import gachi.chagok_server.domain.user.entity.*;
import gachi.chagok_server.domain.user.repository.*;
import gachi.chagok_server.global.exception.*;
import lombok.RequiredArgsConstructor;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.time.*;import java.util.*;
@Service @RequiredArgsConstructor
public class OAuthService {
 private final KakaoOAuthClient kakao;private final GoogleOAuthClient google;private final UserRepository users;
 private final SocialAccountRepository social;private final TokenService tokens;
 @Transactional public OAuthTokenResponse kakaoLogin(String access,boolean keep){return login(SocialProvider.KAKAO,kakao.verify(access),keep);}
 @Transactional public OAuthTokenResponse googleLogin(String idToken,boolean keep){return login(SocialProvider.GOOGLE,google.verify(idToken),keep);}
 private OAuthTokenResponse login(SocialProvider provider,OAuthUserInfo info,boolean keep){
  var linked=social.findByProviderAndProviderUserId(provider,info.providerUserId());boolean fresh=linked.isEmpty();
  User user;if(fresh){
   if(info.phoneNumber()!=null&&users.existsByPhoneNumber(info.phoneNumber()))throw new BusinessException(ErrorCode.CONFLICT);
   user=User.socialUser(info.name()==null?"사용자":info.name());user.setSocialProvider(provider==SocialProvider.KAKAO?LoginType.KAKAO:LoginType.GOOGLE);
   if(info.birthDate()!=null)user.setBirthDateOnce(info.birthDate());
   users.saveAndFlush(user);social.save(new SocialAccount(user,provider,info.providerUserId()));
  }else user=linked.get().getUser();
  TokenResponse t=tokens.issue(user,keep);List<String> missing=new ArrayList<>();
  if(info.name()==null)missing.add("name");if(user.getBirthDate()==null)missing.add("birthDate");if(user.getPhoneNumber()==null)missing.add("phoneNumber");
  boolean under14=user.getBirthDate()!=null&&user.getBirthDate().isAfter(LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(14));
  return new OAuthTokenResponse(t.userId(),t.accessToken(),t.refreshToken(),t.accessTokenExpiresIn(),t.refreshTokenExpiresIn(),fresh,fresh,under14,missing,user.isOnboardingCompleted());
 }
}
