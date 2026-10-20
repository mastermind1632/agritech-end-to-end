package tut.ac.za.AgriFinanceAPIs.consent;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "consent_records")
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false)
    private String farmerId;

    @Column(name = "consent_type", nullable = false)
    private String consentType;

    @Column(nullable = false)
    private boolean granted;

    @Column(nullable = false)
    private String version;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt = LocalDateTime.now();

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "ip_address")
    private String ipAddress;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getConsentType() { return consentType; }
    public void setConsentType(String consentType) { this.consentType = consentType; }
    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
    public void setGrantedAt(LocalDateTime grantedAt) { this.grantedAt = grantedAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
