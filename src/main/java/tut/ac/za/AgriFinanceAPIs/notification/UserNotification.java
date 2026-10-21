package tut.ac.za.AgriFinanceAPIs.notification;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** An in-app notification for any user (orders, disputes, compliance reminders, export alerts, statements). */
@Entity
@Table(name = "user_notifications")
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false, length = 36)
    private String recipientId;

    /** ORDER, DISPUTE, GROUP, COMPLIANCE, EXPORT, STATEMENT, ACCOUNT, SYSTEM */
    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 1000)
    private String body;

    /** The app page to open, e.g. "farmmarket", "companion", "export". */
    @Column(length = 100)
    private String link;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
