package tut.ac.za.AgriFinanceAPIs.notification;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tut.ac.za.AgriFinanceAPIs.common.PageResult;

import java.util.Map;

@RestController
public class UserNotificationController {

    private final UserNotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final LiveEvents live;
    private final ChannelSender channels;

    public UserNotificationController(UserNotificationRepository notifications, NotificationPreferenceRepository preferences,
                                      LiveEvents live, ChannelSender channels) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.live = live;
        this.channels = channels;
    }

    public record Preferences(boolean inApp, boolean email, boolean sms, boolean whatsapp,
                              boolean emailAvailable, boolean smsAvailable) { }

    @GetMapping("/api/me/notifications")
    public PageResult<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "30") int size, Authentication auth) {
        return PageResult.of(notifications.findByRecipientIdOrderByCreatedAtDesc(auth.getName(),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100))), NotificationHub::view);
    }

    @GetMapping("/api/me/notifications/unread-count")
    public Map<String, Long> unread(Authentication auth) {
        return Map.of("unread", notifications.countByRecipientIdAndReadFalse(auth.getName()));
    }

    @PutMapping("/api/me/notifications/{id}/read")
    @Transactional
    public Map<String, Object> markRead(@PathVariable Long id, Authentication auth) {
        UserNotification n = notifications.findById(id)
                .filter(x -> x.getRecipientId().equals(auth.getName()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        n.setRead(true);
        return NotificationHub.view(notifications.save(n));
    }

    @PutMapping("/api/me/notifications/read-all")
    @Transactional
    public Map<String, Integer> markAll(Authentication auth) {
        return Map.of("updated", notifications.markAllRead(auth.getName()));
    }

    @GetMapping("/api/me/notification-preferences")
    public Preferences getPreferences(Authentication auth) {
        NotificationPreference p = preferences.findById(auth.getName()).orElse(NotificationPreference.defaults(auth.getName()));
        return new Preferences(p.isInApp(), p.isEmail(), p.isSms(), p.isWhatsapp(), channels.emailConfigured(), channels.smsConfigured());
    }

    @PutMapping("/api/me/notification-preferences")
    @Transactional
    public Preferences setPreferences(@RequestBody Preferences body, Authentication auth) {
        NotificationPreference p = preferences.findById(auth.getName()).orElse(NotificationPreference.defaults(auth.getName()));
        p.setInApp(true);
        p.setEmail(body.email());
        p.setSms(body.sms());
        p.setWhatsapp(body.whatsapp());
        preferences.save(p);
        return getPreferences(auth);
    }

    /** Step 1 of the live connection: exchange the normal login token for a stream ticket. */
    @PostMapping("/api/live/ticket")
    public Map<String, String> ticket(Authentication auth) {
        return Map.of("ticket", live.issueTicket(auth.getName()));
    }

    /** Step 2: the browser opens new EventSource('/api/live/stream?ticket=...'). Public path, ticket checked here. */
    @GetMapping(value = "/api/live/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String ticket) {
        SseEmitter emitter = live.connect(ticket);
        if (emitter == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Stream ticket expired");
        return emitter;
    }
}
