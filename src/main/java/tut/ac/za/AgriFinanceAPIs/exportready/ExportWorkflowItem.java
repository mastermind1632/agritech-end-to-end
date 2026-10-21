package tut.ac.za.AgriFinanceAPIs.exportready;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "export_workflow_items")
public class ExportWorkflowItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

    @Column(name = "requirement_id", nullable = false)
    private Long requirementId;

    /** TODO, IN_PROGRESS, BLOCKED, DONE, NOT_APPLICABLE */
    @Column(nullable = false, length = 20)
    private String status = "TODO";

    @Column(length = 500)
    private String note;

    @Column(name = "document_id", length = 36)
    private String documentId;

    /** Which version of the requirement the farmer worked against (so changes can be flagged). */
    @Column(name = "requirement_version", nullable = false)
    private int requirementVersion;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getWorkflowId() { return workflowId; }
    public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    public Long getRequirementId() { return requirementId; }
    public void setRequirementId(Long requirementId) { this.requirementId = requirementId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public int getRequirementVersion() { return requirementVersion; }
    public void setRequirementVersion(int requirementVersion) { this.requirementVersion = requirementVersion; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
