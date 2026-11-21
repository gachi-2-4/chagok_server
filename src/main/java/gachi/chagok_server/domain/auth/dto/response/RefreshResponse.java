package gachi.chagok_server.domain.auth.dto.response;
public record RefreshResponse(String accessToken,String refreshToken,long accessTokenExpiresIn,long refreshTokenExpiresIn) {}
