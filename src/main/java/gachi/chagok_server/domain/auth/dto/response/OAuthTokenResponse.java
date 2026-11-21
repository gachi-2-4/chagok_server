package gachi.chagok_server.domain.auth.dto.response;
import java.util.List;
public record OAuthTokenResponse(Long userId,String accessToken,String refreshToken,long accessTokenExpiresIn,
 long refreshTokenExpiresIn,boolean isNewUser,boolean consentRequired,
 boolean guardianConsentRequired,List<String> missingFields,boolean onboardingCompleted) {}
