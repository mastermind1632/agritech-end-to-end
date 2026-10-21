package tut.ac.za.AgriFinanceAPIs.privacy;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A POPIA data-subject request (currently: delete my account and data), handled by an administrator. */
@Entity
@Table(name = "data_requests")
public class DataRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false, length = 36)
    private String farmerId;

    @Column(name = "request_type", nullable = false, length = 20)
    private String requestType;

    /** OPEN, COMPLETED or REJECTED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String reason;

    @Column(name = "handled_by", length = 36)
    private String handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getHandledBy() { return handledBy; }
    public void setHandledBy(String handledBy) { this.handledBy = handledBy; }
    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
