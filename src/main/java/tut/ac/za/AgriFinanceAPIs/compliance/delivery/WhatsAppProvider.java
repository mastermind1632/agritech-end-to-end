package tut.ac.za.AgriFinanceAPIs.compliance.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "whatsapp")
public class WhatsAppProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppProvider.class);

    private final RestClient http;
    private final String phoneId;
    private final String token;

    public WhatsAppProvider(RestClient.Builder builder,
                            @Value("${app.whatsapp.phone-id:}") String phoneId,
                            @Value("${app.whatsapp.token:}") String token) {
        this.http = builder.baseUrl("https://graph.facebook.com/v20.0").build();
        this.phoneId = phoneId;
        this.token = token;
        if (phoneId.isBlank() || token.isBlank()) {
            log.warn("WhatsApp provider selected but phone-id or token missing; sends will fail");
        }
    }

    @Override
    public boolean send(String toPhoneNumber, String message) {
        if (toPhoneNumber == null || toPhoneNumber.isBlank()) {
            log.warn("WhatsApp send skipped: empty phone number");
            return false;
        }
        // Meta requires the number without the leading '+' for wa_id
        String waId = toPhoneNumber.startsWith("+") ? toPhoneNumber.substring(1) : toPhoneNumber;
        Map<String, Object> payload = Map.of(
            "messaging_product", "whatsapp",
            "to", waId,
            "type", "text",
            "text", Map.of("body", message)
        );
        try {
            String response = http.post()
                .uri("/" + phoneId + "/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(String.class);
            log.info("WhatsApp accepted message to {}: {}", waId, response);
            return true;
        } catch (RestClientException ex) {
            log.warn("WhatsApp send failed to {}: {}", waId, ex.getMessage());
            return false;
        }
    }
}
