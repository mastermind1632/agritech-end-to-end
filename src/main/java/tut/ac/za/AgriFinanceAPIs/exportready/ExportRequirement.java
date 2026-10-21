package tut.ac.za.AgriFinanceAPIs.exportready;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One export requirement, tagged with the authority it comes from (PPECB, Department of Agriculture, SARS Customs,
 * destination country, private standard, forwarder). Null product / destination means it applies to all.
 */
@Entity
@Table(name = "export_requirements")
public class ExportRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(name = "product_code", length = 40)
    private String productCode;

    @Column(name = "destination_code", length = 20)
    private String destinationCode;

    @Column(nullable = false)
    private int stage;

    @Column(nullable = false, length = 30)
    private String authority;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(length = 500)
    private String documents;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "est_cost", length = 80)
    private String estCost;

    @Column(name = "source_url", length = 300)
    private String sourceUrl;

    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus = "DRAFT";

    @Column(name = "last_verified")
    private LocalDate lastVerified;

    @Column(name = "verified_by", length = 36)
    private String verifiedBy;

    @Column(nullable = false)
    private int version = 1;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "source_checked_at")
    private LocalDateTime sourceCheckedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public boolean appliesTo(String product, String destination) {
        return active && (productCode == null || productCode.equals(product))
                && (destinationCode == null || destinationCode.equals(destination));
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getDestinationCode() { return destinationCode; }
    public void setDestinationCode(String destinationCode) { this.destinationCode = destinationCode; }
    public int getStage() { return stage; }
    public void setStage(int stage) { this.stage = stage; }
    public String getAuthority() { return authority; }
    public void setAuthority(String authority) { this.authority = authority; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDocuments() { return documents; }
    public void setDocuments(String documents) { this.documents = documents; }
    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public String getEstCost() { return estCost; }
    public void setEstCost(String estCost) { this.estCost = estCost; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public LocalDate getLastVerified() { return lastVerified; }
    public void setLastVerified(LocalDate lastVerified) { this.lastVerified = lastVerified; }
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }
    public LocalDateTime getSourceCheckedAt() { return sourceCheckedAt; }
    public void setSourceCheckedAt(LocalDateTime sourceCheckedAt) { this.sourceCheckedAt = sourceCheckedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
