package tut.ac.za.AgriFinanceAPIs.companion;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** An entry in the farm's compliance calendar. Reminders go out 30, 7 and 1 days before the due date. */
@Entity
@Table(name = "compliance_deadlines")
public class ComplianceDeadline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false, length = 36)
    private String farmerId;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(length = 80)
    private String authority;

    @Column(name = "source_url", length = 300)
    private String sourceUrl;

    /** True when the date is an estimate the farmer should confirm with the authority. */
    @Column(nullable = false)
    private boolean estimated;

    /** True for deadlines the farmer added (permit renewals etc.); false for generated ones. */
    @Column(nullable = false)
    private boolean custom;

    /** UPCOMING, DONE or MISSED */
    @Column(nullable = false, length = 20)
    private String status = "UPCOMING";

    @Column(name = "reminded_30", nullable = false)
    private boolean reminded30;

    @Column(name = "reminded_7", nullable = false)
    private boolean reminded7;

    @Column(name = "reminded_1", nullable = false)
    private boolean reminded1;

    @Column(name = "document_id", length = 36)
    private String documentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public String getAuthority() { return authority; }
    public void setAuthority(String authority) { this.authority = authority; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public boolean isEstimated() { return estimated; }
    public void setEstimated(boolean estimated) { this.estimated = estimated; }
    public boolean isCustom() { return custom; }
    public void setCustom(boolean custom) { this.custom = custom; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isReminded30() { return reminded30; }
    public void setReminded30(boolean reminded30) { this.reminded30 = reminded30; }
    public boolean isReminded7() { return reminded7; }
    public void setReminded7(boolean reminded7) { this.reminded7 = reminded7; }
    public boolean isReminded1() { return reminded1; }
    public void setReminded1(boolean reminded1) { this.reminded1 = reminded1; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
