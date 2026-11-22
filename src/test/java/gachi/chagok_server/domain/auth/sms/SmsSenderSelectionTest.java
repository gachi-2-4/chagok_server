package gachi.chagok_server.domain.auth.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class SmsSenderSelectionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TwilioSmsSender.class, LoggingSmsSender.class)
            .withPropertyValues(
                    "auth.sms.twilio.account-sid=ACtest",
                    "auth.sms.twilio.auth-token=test",
                    "auth.sms.twilio.from=+10000000000");

    @Test
    @DisplayName("provider를 지정하지 않으면 Twilio 발송기를 사용한다")
    void defaultsToTwilio() {
        // given & when & then
        runner.run(context -> {
            assertThat(context).hasSingleBean(SmsSender.class);
            assertThat(context.getBean(SmsSender.class)).isInstanceOf(TwilioSmsSender.class);
        });
    }

    @Test
    @DisplayName("provider가 log이면 로그 발송기만 사용한다")
    void usesLoggingSenderWhenProviderIsLog() {
        // given & when & then
        runner.withPropertyValues("auth.sms.provider=log").run(context -> {
            assertThat(context).hasSingleBean(SmsSender.class);
            assertThat(context.getBean(SmsSender.class)).isInstanceOf(LoggingSmsSender.class);
        });
    }

    @Test
    @DisplayName("로그 발송기는 예외 없이 동작한다")
    void loggingSenderDoesNotThrow() {
        // given
        LoggingSmsSender sender = new LoggingSmsSender();

        // when & then
        sender.send("01012345678", "[차곡] 인증번호 123456 (3분 이내 입력)");
    }
}
