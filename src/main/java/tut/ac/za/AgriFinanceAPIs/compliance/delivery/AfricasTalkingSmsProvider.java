package tut.ac.za.AgriFinanceAPIs.compliance.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "africas-talking")
public class AfricasTalkingSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(AfricasTalkingSmsProvider.class);
    private static final String ENDPOINT = "https://api.africastalking.com/version1/messaging";

    private final RestClient http;
    private final String apiKey;
    private final String username;
    private final String senderId;

    public AfricasTalkingSmsProvider(
            RestClient.Builder builder,
            @Value("${app.sms.africas-talking.api-key:}") String apiKey,
            @Value("${app.sms.africas-talking.username:}") String username,
            @Value("${app.sms.africas-talking.sender-id:}") String senderId) {
        this.http = builder.baseUrl(ENDPOINT).build();
        this.apiKey = apiKey;
        this.username = username;
        this.senderId = senderId;
        if (apiKey.isBlank() || username.isBlank()) {
            log.warn("Africa's Talking provider selected but api-key or username is missing; sends will fail");
        }
    }

    @Override
    public boolean send(String toPhoneNumber, String message) {
        if (toPhoneNumber == null || toPhoneNumber.isBlank()) {
            log.warn("SMS send skipped: empty phone number");
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("to", toPhoneNumber);
        form.add("message", message);
        if (!senderId.isBlank()) {
            form.add("from", senderId);
        }
        try {
            String response = http.post()
                    .header("apiKey", apiKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            log.info("Africa's Talking accepted SMS to {}: {}", toPhoneNumber, response);
            return true;
        } catch (RestClientException ex) {
            log.warn("Africa's Talking send failed to {}: {}", toPhoneNumber, ex.getMessage());
            return false;
        }
    }
}
