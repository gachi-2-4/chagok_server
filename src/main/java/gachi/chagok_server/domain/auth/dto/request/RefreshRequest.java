package gachi.chagok_server.domain.auth.dto.request;
import jakarta.validation.constraints.*;
public record RefreshRequest(@NotBlank String refreshToken) {}
