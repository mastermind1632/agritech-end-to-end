package tut.ac.za.AgriFinanceAPIs.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tut.ac.za.AgriFinanceAPIs.common.Roles;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one notification service every module uses: stores the in-app notification, pushes it live to open
 * browsers, then hands it to the email / SMS / WhatsApp sender according to the user's preferences.
 */
@Service
public class NotificationHub {

    private static final Logger log = LoggerFactory.getLogger(NotificationHub.class);

    private final UserNotificationRepository notifications;
    private final FarmerRepository farmers;
    private final LiveEvents live;
    private final ChannelSender channels;

    public NotificationHub(UserNotificationRepository notifications, FarmerRepository farmers, LiveEvents live,
                           ChannelSender channels) {
        this.notifications = notifications;
        this.farmers = farmers;
        this.live = live;
        this.channels = channels;
    }

    public UserNotification notify(String recipientId, String category, String title, String body, String link) {
        if (recipientId == null) return null;
        UserNotification n = new UserNotification();
        n.setRecipientId(recipientId);
        n.setCategory(category);
        n.setTitle(title.length() > 150 ? title.substring(0, 150) : title);
        n.setBody(body != null && body.length() > 1000 ? body.substring(0, 1000) : body);
        n.setLink(link);
        UserNotification saved = notifications.save(n);
        afterCommit(() -> {
            live.send(recipientId, "notification", view(saved));
            channels.dispatch(saved.getId());
        });
        return saved;
    }

    public void notifyAdmins(String category, String title, String body, String link) {
        for (Farmer admin : farmers.findByRole(Roles.ADMIN)) notify(admin.getId(), category, title, body, link);
    }

    /** Live-only event (no stored notification), e.g. "a new chat message arrived". */
    public void push(String userId, String event, Object data) {
        afterCommit(() -> live.send(userId, event, data));
    }

    public static Map<String, Object> view(UserNotification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("category", n.getCategory());
        m.put("title", n.getTitle());
        m.put("body", n.getBody());
        m.put("link", n.getLink());
        m.put("read", n.isRead());
        m.put("createdAt", n.getCreatedAt());
        return m;
    }

    /** Only push and send once the database change is committed, so users never see something that rolled back. */
    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { safe(r); }
            });
        } else {
            safe(r);
        }
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            log.warn("Notification side effect failed: {}", e.getMessage());
        }
    }
}
