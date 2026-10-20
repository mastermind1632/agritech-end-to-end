package tut.ac.za.AgriFinanceAPIs.compliance.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "whatsapp-mock")
public class MockWhatsAppProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppProvider.class);

    @Override
    public boolean send(String toPhoneNumber, String message) {
        log.info("[MOCK WHATSAPP] to={} message={}", toPhoneNumber, message);
        return true;
    }
}
