package gachi.chagok_server.domain.auth.dto.request;
import jakarta.validation.constraints.*;
public record PhoneRequest(@NotBlank @Pattern(regexp = "^01[016789]\\d{7,8}$") String phoneNumber) {}
