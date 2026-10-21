package tut.ac.za.AgriFinanceAPIs.privacy;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** POPIA consent log. Append-only: a change of mind adds a new row, so the history can be shown to a regulator. */
@Entity
@Table(name = "consent_records")
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false, length = 36)
    private String farmerId;

    @Column(nullable = false, length = 40)
    private String purpose;

    @Column(nullable = false)
    private boolean granted;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(length = 64)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
