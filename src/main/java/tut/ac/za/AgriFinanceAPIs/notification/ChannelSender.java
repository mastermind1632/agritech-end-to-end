package tut.ac.za.AgriFinanceAPIs.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;

import java.util.Map;

/**
 * Sends notifications outside the app: email (SMTP), SMS (Africa's Talking) and WhatsApp (Meta Cloud API).
 * Every channel is optional and switched on purely by configuration; unconfigured channels are logged as SKIPPED.
 * Runs asynchronously so a slow provider never slows down the user's request.
 */
@Component
public class ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(ChannelSender.class);

    private final ObjectProvider<JavaMailSender> mail;
    private final FarmerRepository farmers;
    private final NotificationPreferenceRepository preferences;
    private final NotificationDeliveryRepository deliveries;
    private final UserNotificationRepository notifications;
    private final RestClient http;
    private final String mailFrom;
    private final String mailHost;
    private final String smsUser;
    private final String smsKey;
    private final String smsSender;
    private final String smsUrl;
    private final String waToken;
    private final String waPhoneId;
    private final String waVersion;

    public ChannelSender(ObjectProvider<JavaMailSender> mail, FarmerRepository farmers,
                         NotificationPreferenceRepository preferences, NotificationDeliveryRepository deliveries,
                         UserNotificationRepository notifications, RestClient.Builder builder,
                         @Value("${app.mail.from:no-reply@agritech.local}") String mailFrom,
                         @Value("${spring.mail.host:}") String mailHost,
                         @Value("${app.sms.africastalking.username:}") String smsUser,
                         @Value("${app.sms.africastalking.api-key:}") String smsKey,
                         @Value("${app.sms.africastalking.sender-id:}") String smsSender,
                         @Value("${app.sms.africastalking.url:https://api.africastalking.com/version1/messaging}") String smsUrl,
                         @Value("${app.whatsapp.token:}") String waToken,
                         @Value("${app.whatsapp.phone-number-id:}") String waPhoneId,
                         @Value("${app.whatsapp.api-version:v20.0}") String waVersion) {
        this.mail = mail;
        this.farmers = farmers;
        this.preferences = preferences;
        this.deliveries = deliveries;
        this.notifications = notifications;
        this.http = builder.build();
        this.mailFrom = mailFrom;
        this.mailHost = mailHost == null ? "" : mailHost.trim();
        this.smsUser = smsUser;
        this.smsKey = smsKey;
        this.smsSender = smsSender;
        this.smsUrl = smsUrl;
        this.waToken = waToken;
        this.waPhoneId = waPhoneId;
        this.waVersion = waVersion;
    }

    @Async
    public void dispatch(Long notificationId) {
        UserNotification n = notifications.findById(notificationId).orElse(null);
        if (n == null) return;
        Farmer f = farmers.findById(n.getRecipientId()).orElse(null);
        if (f == null) return;
        NotificationPreference p = preferences.findById(f.getId()).orElse(NotificationPreference.defaults(f.getId()));
        String text = n.getTitle() + (n.getBody() == null ? "" : "\n" + n.getBody());
        record(n.getId(), "EMAIL", p.isEmail() ? email(f.getEmail(), "AgriTech: " + n.getTitle(), text) : skipped("opted out"));
        record(n.getId(), "SMS", p.isSms() ? sms(f.getContact(), shorten(text, 300)) : skipped("opted out"));
        record(n.getId(), "WHATSAPP", p.isWhatsapp() ? whatsapp(f.getContact(), text) : skipped("opted out"));
    }

    /** Security messages (password reset links) go out regardless of marketing preferences. */
    @Async
    public void sendSecurityMessage(String email, String phone, String subject, String text) {
        String[] e = email(email, subject, text);
        String[] s = e[0].equals("SENT") ? skipped("email sent") : sms(phone, shorten(text, 300));
        log.info("Security message '{}': email={} sms={}", subject, e[0], s[0]);
    }

    public boolean emailConfigured() { return !mailHost.isEmpty() && mail.getIfAvailable() != null; }

    public boolean smsConfigured() { return !smsUser.isBlank() && !smsKey.isBlank(); }

    private String[] email(String to, String subject, String text) {
        JavaMailSender sender = emailConfigured() ? mail.getIfAvailable() : null;
        if (sender == null) return skipped("email not configured (set SPRING_MAIL_HOST)");
        if (to == null || to.isBlank()) return skipped("no email address on profile");
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(mailFrom);
            m.setTo(to);
            m.setSubject(subject);
            m.setText(text);
            sender.send(m);
            return new String[] { "SENT", to };
        } catch (RuntimeException ex) {
            log.warn("Email to {} failed: {}", to, ex.getMessage());
            return new String[] { "FAILED", ex.getMessage() };
        }
    }

    private String[] sms(String contact, String text) {
        if (!smsConfigured()) return skipped("SMS not configured (set AFRICASTALKING_USERNAME and AFRICASTALKING_API_KEY)");
        String phone = toE164(contact);
        if (phone == null) return skipped("contact is not a phone number");
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("username", smsUser);
            form.add("to", phone);
            form.add("message", text);
            if (!smsSender.isBlank()) form.add("from", smsSender);
            http.post().uri(smsUrl).header("apiKey", smsKey).accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
            return new String[] { "SENT", phone };
        } catch (RuntimeException ex) {
            log.warn("SMS to {} failed: {}", phone, ex.getMessage());
            return new String[] { "FAILED", ex.getMessage() };
        }
    }

    private String[] whatsapp(String contact, String text) {
        if (waToken.isBlank() || waPhoneId.isBlank()) return skipped("WhatsApp not configured (set WHATSAPP_TOKEN and WHATSAPP_PHONE_NUMBER_ID)");
        String phone = toE164(contact);
        if (phone == null) return skipped("contact is not a phone number");
        try {
            Map<String, Object> body = Map.of("messaging_product", "whatsapp", "to", phone.substring(1), "type", "text",
                    "text", Map.of("body", shorten(text, 1000)));
            http.post().uri("https://graph.facebook.com/" + waVersion + "/" + waPhoneId + "/messages")
                    .header("Authorization", "Bearer " + waToken).contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().toBodilessEntity();
            return new String[] { "SENT", phone };
        } catch (RuntimeException ex) {
            log.warn("WhatsApp to {} failed: {}", phone, ex.getMessage());
            return new String[] { "FAILED", ex.getMessage() };
        }
    }

    private void record(Long notificationId, String channel, String[] result) {
        try {
            deliveries.save(new NotificationDelivery(notificationId, channel, result[0], result[1]));
        } catch (RuntimeException ex) {
            log.debug("Could not record delivery: {}", ex.getMessage());
        }
    }

    private static String[] skipped(String why) { return new String[] { "SKIPPED", why }; }

    /** South African numbers: 071 234 5678 / 27712345678 / +27 71 234 5678 -> +27712345678. */
    static String toE164(String contact) {
        if (contact == null) return null;
        String d = contact.replaceAll("[^0-9+]", "");
        if (d.startsWith("+") && d.length() >= 11) return d;
        if (d.startsWith("27") && d.length() == 11) return "+" + d;
        if (d.startsWith("0") && d.length() == 10) return "+27" + d.substring(1);
        return null;
    }

    private static String shorten(String s, int max) { return s.length() > max ? s.substring(0, max - 1) + "…" : s; }
}
