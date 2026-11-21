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

import java.util.Optional;

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
}
