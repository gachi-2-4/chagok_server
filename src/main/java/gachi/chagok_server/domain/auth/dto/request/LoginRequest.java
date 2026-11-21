package gachi.chagok_server.domain.auth.dto.request;
import jakarta.validation.constraints.*;
public record LoginRequest(@NotBlank String phoneNumber,@NotBlank String password,Boolean autoLogin) {
 public boolean keepLogin(){return autoLogin==null||autoLogin;}
}
