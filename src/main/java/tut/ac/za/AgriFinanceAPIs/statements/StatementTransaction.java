package tut.ac.za.AgriFinanceAPIs.statements;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One line read from a statement, with its category, confidence and review flags. amount: negative = money out. */
@Entity
@Table(name = "statement_transactions")
public class StatementTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "statement_id", nullable = false, length = 36)
    private String statementId;

    @Column(name = "farmer_id", nullable = false, length = 36)
    private String farmerId;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(nullable = false, length = 300)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(precision = 14, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false)
    private double confidence;

    /** RULE, LEARNED, ML, AI, USER, MARKET_MATCH or DEFAULT */
    @Column(name = "category_source", nullable = false, length = 20)
    private String categorySource;

    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "direction_certain", nullable = false)
    private boolean directionCertain = true;

    @Column(length = 60)
    private String crop;

    /** DUPLICATE or UNUSUAL (null = no flag). Duplicates are left out of the ledger import. */
    @Column(length = 20)
    private String flag;

    @Column(name = "flag_reason", length = 300)
    private String flagReason;

    @Column(name = "matched_order_id")
    private Long matchedOrderId;

    /** EXPENSE or INCOME once imported into My Finances. */
    @Column(name = "ledger_type", length = 10)
    private String ledgerType;

    @Column(name = "ledger_id")
    private Long ledgerId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getStatementId() { return statementId; }
    public void setStatementId(String statementId) { this.statementId = statementId; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public LocalDate getTxnDate() { return txnDate; }
    public void setTxnDate(LocalDate txnDate) { this.txnDate = txnDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getCategorySource() { return categorySource; }
    public void setCategorySource(String categorySource) { this.categorySource = categorySource; }
    public boolean isNeedsReview() { return needsReview; }
    public void setNeedsReview(boolean needsReview) { this.needsReview = needsReview; }
    public boolean isDirectionCertain() { return directionCertain; }
    public void setDirectionCertain(boolean directionCertain) { this.directionCertain = directionCertain; }
    public String getCrop() { return crop; }
    public void setCrop(String crop) { this.crop = crop; }
    public String getFlag() { return flag; }
    public void setFlag(String flag) { this.flag = flag; }
    public String getFlagReason() { return flagReason; }
    public void setFlagReason(String flagReason) { this.flagReason = flagReason; }
    public Long getMatchedOrderId() { return matchedOrderId; }
    public void setMatchedOrderId(Long matchedOrderId) { this.matchedOrderId = matchedOrderId; }
    public String getLedgerType() { return ledgerType; }
    public void setLedgerType(String ledgerType) { this.ledgerType = ledgerType; }
    public Long getLedgerId() { return ledgerId; }
    public void setLedgerId(Long ledgerId) { this.ledgerId = ledgerId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
