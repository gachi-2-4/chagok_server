package gachi.chagok_server.domain.auth.dto.request;
import jakarta.validation.constraints.NotBlank;
public record OAuthLoginRequest(String kakaoAccessToken,String googleIdToken,Boolean autoLogin) {
 public boolean keepLogin(){return autoLogin==null||autoLogin;}
}
