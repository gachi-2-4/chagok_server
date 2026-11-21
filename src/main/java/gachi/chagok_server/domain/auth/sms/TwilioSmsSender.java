package gachi.chagok_server.domain.auth.sms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class TwilioSmsSender implements SmsSender {
    private final String accountSid;
    private final String authToken;
    private final String from;
    private final RestClient client = RestClient.create();

    public TwilioSmsSender(@Value("${auth.sms.twilio.account-sid}") String accountSid,
                           @Value("${auth.sms.twilio.auth-token}") String authToken,
                           @Value("${auth.sms.twilio.from}") String from) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.from = from;
    }

    @Override
    public void send(String phoneNumber, String message) {
        String international = "+82" + phoneNumber.substring(1);
        LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("To", international);
        body.add("From", from);
        body.add("Body", message);
        client.post().uri("https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                .headers(headers -> headers.setBasicAuth(accountSid, authToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body).retrieve().toBodilessEntity();
    }
}
