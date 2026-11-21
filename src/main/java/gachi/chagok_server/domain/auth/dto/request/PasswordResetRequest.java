package gachi.chagok_server.domain.auth.dto.request;
import jakarta.validation.constraints.*;
public record PasswordResetRequest(@NotBlank String phoneNumber,@NotBlank String verificationToken,
 @NotBlank @Pattern(regexp="^(?=.{8,64}$)(?=.*[0-9])(?=.*[^a-zA-Z0-9]).*$") String newPassword) {}
