package tut.ac.za.AgriFinanceAPIs.exportready;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A proposed or detected change to a requirement, waiting for a compliance editor (never auto-published). */
@Entity
@Table(name = "export_requirement_changes")
public class ExportRequirementChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requirement_id", nullable = false)
    private Long requirementId;

    /** MONITOR (source page changed) or MANUAL (proposed by a person). */
    @Column(nullable = false, length = 20)
    private String source;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(name = "proposed_title", length = 200)
    private String proposedTitle;

    @Column(name = "proposed_description", length = 2000)
    private String proposedDescription;

    /** PENDING, APPROVED or REJECTED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "proposed_by", length = 36)
    private String proposedBy;

    @Column(name = "reviewed_by", length = 36)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getRequirementId() { return requirementId; }
    public void setRequirementId(Long requirementId) { this.requirementId = requirementId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getProposedTitle() { return proposedTitle; }
    public void setProposedTitle(String proposedTitle) { this.proposedTitle = proposedTitle; }
    public String getProposedDescription() { return proposedDescription; }
    public void setProposedDescription(String proposedDescription) { this.proposedDescription = proposedDescription; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getProposedBy() { return proposedBy; }
    public void setProposedBy(String proposedBy) { this.proposedBy = proposedBy; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
