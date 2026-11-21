package gachi.chagok_server.domain.auth.dto.request;
import jakarta.validation.constraints.*;
public record LogoutRequest(@NotBlank String refreshToken,@NotBlank String deviceId) {}
