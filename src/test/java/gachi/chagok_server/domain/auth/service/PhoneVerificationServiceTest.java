package gachi.chagok_server.domain.auth.service;

import gachi.chagok_server.domain.auth.entity.VerificationPurpose;
import gachi.chagok_server.domain.auth.repository.PhoneVerificationRepository;
import gachi.chagok_server.domain.auth.repository.VerificationGrantRepository;
import gachi.chagok_server.domain.auth.sms.SmsSender;
import gachi.chagok_server.domain.user.repository.UserRepository;
import gachi.chagok_server.global.exception.BusinessException;
import gachi.chagok_server.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import gachi.chagok_server.domain.auth.dto.response.VerificationCodeResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PhoneVerificationServiceTest {

    @Mock PhoneVerificationRepository codes;
    @Mock VerificationGrantRepository grants;
    @Mock UserRepository users;
    @Mock SmsSender sms;
    @InjectMocks PhoneVerificationService service;

    @Test
    @DisplayName("발송 이력이 없는 번호로 인증을 확인하면 404가 아닌 400 예외가 발생한다")
    void confirmWithoutSentCodeDoesNotRevealExistence() {
        // given
        given(codes.findLocked("01012345678", VerificationPurpose.PASSWORD_RESET)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.confirm("01012345678", "123456", VerificationPurpose.PASSWORD_RESET))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("log 모드에서는 발송 응답에 6자리 인증번호가 포함된다")
    void sendExposesCodeInLogMode() {
        // given
        ReflectionTestUtils.setField(service, "secret", "test-secret");
        ReflectionTestUtils.setField(service, "smsProvider", "log");
        given(users.existsByPhoneNumber("01012345678")).willReturn(false);
        given(codes.findLocked("01012345678", VerificationPurpose.SIGNUP)).willReturn(Optional.empty());

        // when
        VerificationCodeResponse response = service.send("01012345678", VerificationPurpose.SIGNUP);

        // then
        assertThat(response.code()).matches("\\d{6}");
    }

    @Test
    @DisplayName("twilio 모드에서는 발송 응답에 인증번호가 포함되지 않는다")
    void sendHidesCodeInTwilioMode() {
        // given
        ReflectionTestUtils.setField(service, "secret", "test-secret");
        ReflectionTestUtils.setField(service, "smsProvider", "twilio");
        given(users.existsByPhoneNumber("01012345678")).willReturn(false);
        given(codes.findLocked("01012345678", VerificationPurpose.SIGNUP)).willReturn(Optional.empty());

        // when
        VerificationCodeResponse response = service.send("01012345678", VerificationPurpose.SIGNUP);

        // then
        assertThat(response.code()).isNull();
    }
}
