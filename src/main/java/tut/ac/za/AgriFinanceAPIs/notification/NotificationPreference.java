package tut.ac.za.AgriFinanceAPIs.notification;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Which channels a user wants: in-app is always on in practice; email, SMS and WhatsApp are optional. */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @Column(name = "farmer_id", length = 36)
    private String farmerId;

    @Column(name = "in_app", nullable = false)
    private boolean inApp = true;

    @Column(nullable = false)
    private boolean email = true;

    @Column(nullable = false)
    private boolean sms;

    @Column(nullable = false)
    private boolean whatsapp;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public static NotificationPreference defaults(String farmerId) {
        NotificationPreference p = new NotificationPreference();
        p.farmerId = farmerId;
        return p;
    }

    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public boolean isInApp() { return inApp; }
    public void setInApp(boolean inApp) { this.inApp = inApp; }
    public boolean isEmail() { return email; }
    public void setEmail(boolean email) { this.email = email; }
    public boolean isSms() { return sms; }
    public void setSms(boolean sms) { this.sms = sms; }
    public boolean isWhatsapp() { return whatsapp; }
    public void setWhatsapp(boolean whatsapp) { this.whatsapp = whatsapp; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
