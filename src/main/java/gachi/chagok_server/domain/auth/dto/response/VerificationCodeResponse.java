package gachi.chagok_server.domain.auth.dto.response;
public record VerificationCodeResponse(String phoneNumber,long expiresIn,long resendAvailableIn) {}
