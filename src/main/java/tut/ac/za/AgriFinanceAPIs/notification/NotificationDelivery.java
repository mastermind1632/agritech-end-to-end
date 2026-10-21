package tut.ac.za.AgriFinanceAPIs.notification;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Delivery log per channel: SENT, SKIPPED (not configured / opted out) or FAILED. */
@Entity
@Table(name = "notification_deliveries")
public class NotificationDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false)
    private Long notificationId;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 300)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public NotificationDelivery() { }

    public NotificationDelivery(Long notificationId, String channel, String status, String detail) {
        this.notificationId = notificationId;
        this.channel = channel;
        this.status = status;
        this.detail = detail != null && detail.length() > 300 ? detail.substring(0, 300) : detail;
    }

    public Long getId() { return id; }
    public Long getNotificationId() { return notificationId; }
    public String getChannel() { return channel; }
    public String getStatus() { return status; }
    public String getDetail() { return detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
