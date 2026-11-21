package gachi.chagok_server.domain.auth.dto.response;
public record TokenResponse(Long userId,String accessToken,String refreshToken,long accessTokenExpiresIn,
 long refreshTokenExpiresIn,boolean onboardingCompleted) {}
