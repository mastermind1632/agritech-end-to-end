package tut.ac.za.AgriFinanceAPIs.statements;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** An uploaded bank statement and the result of reading it: PROCESSING -> REVIEW -> CONFIRMED (or FAILED). */
@Entity
@Table(name = "bank_statements")
public class BankStatement {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "farmer_id", nullable = false, length = 36)
    private String farmerId;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    /** CSV, PDF or IMAGE */
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    /** csv, pdf-text or ocr */
    @Column(name = "extraction_method", length = 20)
    private String extractionMethod;

    @Column(length = 40)
    private String bank;

    @Column(name = "account_hint", length = 8)
    private String accountHint;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "opening_balance", precision = 14, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", precision = 14, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "computed_closing", precision = 14, scale = 2)
    private BigDecimal computedClosing;

    /** PASSED, FAILED or UNKNOWN: opening balance + transactions = closing balance? */
    @Column(name = "balance_check", nullable = false, length = 10)
    private String balanceCheck = "UNKNOWN";

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 1000)
    private String message;

    @Column(name = "transaction_count", nullable = false)
    private int transactionCount;

    @Column(name = "original_document_id", length = 36)
    private String originalDocumentId;

    @Column(name = "consent_id")
    private Long consentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getExtractionMethod() { return extractionMethod; }
    public void setExtractionMethod(String extractionMethod) { this.extractionMethod = extractionMethod; }
    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }
    public String getAccountHint() { return accountHint; }
    public void setAccountHint(String accountHint) { this.accountHint = accountHint; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public void setClosingBalance(BigDecimal closingBalance) { this.closingBalance = closingBalance; }
    public BigDecimal getComputedClosing() { return computedClosing; }
    public void setComputedClosing(BigDecimal computedClosing) { this.computedClosing = computedClosing; }
    public String getBalanceCheck() { return balanceCheck; }
    public void setBalanceCheck(String balanceCheck) { this.balanceCheck = balanceCheck; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message != null && message.length() > 1000 ? message.substring(0, 1000) : message; }
    public int getTransactionCount() { return transactionCount; }
    public void setTransactionCount(int transactionCount) { this.transactionCount = transactionCount; }
    public String getOriginalDocumentId() { return originalDocumentId; }
    public void setOriginalDocumentId(String originalDocumentId) { this.originalDocumentId = originalDocumentId; }
    public Long getConsentId() { return consentId; }
    public void setConsentId(Long consentId) { this.consentId = consentId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
}
