package tut.ac.za.AgriFinanceAPIs.companion;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One piece of guidance content. Editable by admins without a code release; carries its source and review status. */
@Entity
@Table(name = "companion_steps")
public class CompanionStep {

    @Id
    @Column(length = 40)
    private String code;

    @Column(nullable = false)
    private int stage;

    @Column(name = "stage_name", nullable = false, length = 60)
    private String stageName;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(length = 1000)
    private String why;

    @Column(length = 80)
    private String authority;

    @Column(name = "rule_key", nullable = false, length = 80)
    private String ruleKey = "ALWAYS";

    @Column(name = "link_url", length = 300)
    private String linkUrl;

    @Column(name = "link_label", length = 80)
    private String linkLabel;

    /** In-app page this step points to (statements, export, vault, companion). */
    @Column(name = "module_link", length = 30)
    private String moduleLink;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active = true;

    /** DRAFT until reviewed by a qualified person, then VERIFIED with a date. */
    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus = "DRAFT";

    @Column(name = "last_verified")
    private LocalDate lastVerified;

    @Column(name = "verified_by", length = 36)
    private String verifiedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public int getStage() { return stage; }
    public void setStage(int stage) { this.stage = stage; }
    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getWhy() { return why; }
    public void setWhy(String why) { this.why = why; }
    public String getAuthority() { return authority; }
    public void setAuthority(String authority) { this.authority = authority; }
    public String getRuleKey() { return ruleKey; }
    public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }
    public String getLinkUrl() { return linkUrl; }
    public void setLinkUrl(String linkUrl) { this.linkUrl = linkUrl; }
    public String getLinkLabel() { return linkLabel; }
    public void setLinkLabel(String linkLabel) { this.linkLabel = linkLabel; }
    public String getModuleLink() { return moduleLink; }
    public void setModuleLink(String moduleLink) { this.moduleLink = moduleLink; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public LocalDate getLastVerified() { return lastVerified; }
    public void setLastVerified(LocalDate lastVerified) { this.lastVerified = lastVerified; }
    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
