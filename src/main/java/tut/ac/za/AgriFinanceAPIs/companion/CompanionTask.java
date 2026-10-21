package tut.ac.za.AgriFinanceAPIs.companion;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A farmer's progress on one guidance step: TODO, IN_PROGRESS, DONE or NOT_APPLICABLE. */
@Entity
@Table(name = "companion_tasks")
public class CompanionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false, length = 36)
    private String farmerId;

    @Column(name = "step_code", nullable = false, length = 40)
    private String stepCode;

    @Column(nullable = false, length = 20)
    private String status = "TODO";

    @Column(length = 500)
    private String note;

    @Column(name = "document_id", length = 36)
    private String documentId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getStepCode() { return stepCode; }
    public void setStepCode(String stepCode) { this.stepCode = stepCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
