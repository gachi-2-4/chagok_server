package gachi.chagok_server.domain.auth.dto.request;
import jakarta.validation.constraints.*;
import gachi.chagok_server.domain.auth.entity.VerificationPurpose;
public record VerifyPhoneRequest(@NotBlank String phoneNumber,@NotBlank @Pattern(regexp="\\d{6}") String code,@NotNull VerificationPurpose purpose) {}
