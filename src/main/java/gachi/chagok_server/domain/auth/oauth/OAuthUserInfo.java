package gachi.chagok_server.domain.auth.oauth;
import java.time.LocalDate;
public record OAuthUserInfo(String providerUserId,String name,String phoneNumber,LocalDate birthDate) {}
