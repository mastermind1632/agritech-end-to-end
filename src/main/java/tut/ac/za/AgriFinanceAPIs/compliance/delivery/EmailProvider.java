package tut.ac.za.AgriFinanceAPIs.compliance.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "email")
public class EmailProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailProvider.class);

    private final JavaMailSender mailer;
    private final String from;

    public EmailProvider(JavaMailSender mailer,
                         @Value("${app.email.from:noreply@agritech.local}") String from) {
        this.mailer = mailer;
        this.from = from;
    }

    @Override
    public boolean send(String toPhoneNumber, String message) {
        if (toPhoneNumber == null || !toPhoneNumber.contains("@")) {
            log.warn("Email delivery skipped: contact is not an email address ({})", toPhoneNumber);
            return false;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(toPhoneNumber);
            msg.setSubject("AgriTech reminder");
            msg.setText(message);
            mailer.send(msg);
            log.info("Email sent to {}", toPhoneNumber);
            return true;
        } catch (Exception ex) {
            log.warn("Email send failed to {}: {}", toPhoneNumber, ex.getMessage());
            return false;
        }
    }
}
