package gachi.chagok_server.domain.auth.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 인증번호 발송 응답. code는 시연·테스트용(auth.sms.provider=log)일 때만 채워지고, 그 외에는 응답에서 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VerificationCodeResponse(String phoneNumber, long expiresIn, long resendAvailableIn, String code) {
    public VerificationCodeResponse(String phoneNumber, long expiresIn, long resendAvailableIn) {
        this(phoneNumber, expiresIn, resendAvailableIn, null);
    }
}
