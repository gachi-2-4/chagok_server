package gachi.chagok_server.domain.auth.dto.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public record SignUpRequest(
 @NotBlank @Pattern(regexp="^01[016789]\\d{7,8}$") String phoneNumber,
 @NotBlank @Size(min=1,max=20) String name,
 @NotNull @PastOrPresent LocalDate birthDate,
 @NotBlank @Pattern(regexp="^(?=.{8,64}$)(?=.*[0-9])(?=.*[^a-zA-Z0-9]).*$") String password,
 @NotBlank String verificationToken,
 @NotNull @Valid Consents consents
) {
 public record Consents(boolean termsOfService,boolean privacyPolicy,boolean guardianConsent,boolean overseasTransfer,boolean marketing) {}
}
