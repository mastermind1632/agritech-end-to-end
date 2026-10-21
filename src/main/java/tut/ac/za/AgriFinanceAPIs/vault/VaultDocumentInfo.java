package tut.ac.za.AgriFinanceAPIs.vault;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Document details without the file bytes (what lists and other modules see). */
public record VaultDocumentInfo(String id, String ownerId, String title, String docType, String fileName,
                                String contentType, Long sizeBytes, LocalDate expiresOn, String linkedModule,
                                String linkedRef, LocalDateTime createdAt) {

    public Long daysToExpiry() {
        return expiresOn == null ? null : java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiresOn);
    }

    public String expiryStatus() {
        Long d = daysToExpiry();
        if (d == null) return "NONE";
        if (d < 0) return "EXPIRED";
        if (d <= 30) return "EXPIRING";
        return "VALID";
    }
}
