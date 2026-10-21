package tut.ac.za.AgriFinanceAPIs.farmer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "farmers")
public class Farmer {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 150)
    private String location;

    @Column(length = 100, unique = true)
    private String contact;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    /** FARMER (default), BUYER, SUPPLIER or ADMIN. Null (very old rows) is treated as FARMER. */
    @Column(length = 20)
    private String role;

    @Column(length = 150)
    private String email;

    /** Base32 TOTP secret for administrator multi-factor authentication. Never sent to the browser. */
    @Column(name = "mfa_secret", length = 64)
    private String mfaSecret;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    /** ACTIVE or SUSPENDED (set by an administrator). */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "preferred_language", nullable = false, length = 5)
    private String preferredLanguage = "en";

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Farmer() {
    }

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (role == null || role.isBlank()) role = "FARMER";
        if (status == null || status.isBlank()) status = "ACTIVE";
        if (preferredLanguage == null || preferredLanguage.isBlank()) preferredLanguage = "en";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role == null || role.isBlank() ? "FARMER" : role; }
    public void setRole(String role) { this.role = role; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getMfaSecret() { return mfaSecret; }
    public void setMfaSecret(String mfaSecret) { this.mfaSecret = mfaSecret; }

    public boolean isMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(boolean mfaEnabled) { this.mfaEnabled = mfaEnabled; }

    public String getStatus() { return status == null ? "ACTIVE" : status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isActive() { return !"SUSPENDED".equals(status); }

    public String getPreferredLanguage() { return preferredLanguage == null ? "en" : preferredLanguage; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = preferredLanguage; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
