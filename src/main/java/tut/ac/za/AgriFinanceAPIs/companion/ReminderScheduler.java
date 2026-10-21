package tut.ac.za.AgriFinanceAPIs.companion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tut.ac.za.AgriFinanceAPIs.notification.NotificationHub;
import tut.ac.za.AgriFinanceAPIs.vault.VaultDocumentInfo;
import tut.ac.za.AgriFinanceAPIs.vault.VaultDocumentRepository;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The compliance reminder engine: every morning, reminders 30, 7 and 1 days before each deadline and before
 * documents in the vault expire. Delivery channels follow each user's notification preferences.
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ComplianceDeadlineRepository deadlines;
    private final VaultDocumentRepository documents;
    private final NotificationHub notifications;

    public ReminderScheduler(ComplianceDeadlineRepository deadlines, VaultDocumentRepository documents, NotificationHub notifications) {
        this.deadlines = deadlines;
        this.documents = documents;
        this.notifications = notifications;
    }

    @Scheduled(cron = "${app.reminders.cron:0 0 6 * * *}")
    @Transactional
    public void run() {
        LocalDate today = LocalDate.now();
        int sent = 0;
        for (ComplianceDeadline d : deadlines.findByStatusAndDueDateLessThanEqual("UPCOMING", today.plusDays(30))) {
            long days = ChronoUnit.DAYS.between(today, d.getDueDate());
            if (days < 0) {
                d.setStatus("MISSED");
                deadlines.save(d);
                notifications.notify(d.getFarmerId(), "COMPLIANCE", "Missed: " + d.getTitle(),
                        "The due date was " + d.getDueDate() + ". If it is done, mark it as done in Business Builder.", "companion");
                sent++;
                continue;
            }
            boolean send = false;
            if (days <= 1 && !d.isReminded1()) { d.setReminded1(true); d.setReminded7(true); d.setReminded30(true); send = true; }
            else if (days <= 7 && !d.isReminded7()) { d.setReminded7(true); d.setReminded30(true); send = true; }
            else if (days <= 30 && !d.isReminded30()) { d.setReminded30(true); send = true; }
            if (send) {
                deadlines.save(d);
                notifications.notify(d.getFarmerId(), "COMPLIANCE",
                        (days == 0 ? "Due today: " : "Due in " + days + " day" + (days == 1 ? "" : "s") + ": ") + d.getTitle(),
                        (d.getAuthority() == null ? "" : d.getAuthority() + ". ") + (d.isEstimated() ? "This date is an estimate - confirm it with the authority." : ""),
                        "companion");
                sent++;
            }
        }
        for (VaultDocumentInfo doc : documents.expiringBetween(today, today.plusDays(30))) {
            long days = ChronoUnit.DAYS.between(today, doc.expiresOn());
            if (days == 30 || days == 7 || days == 1) {
                notifications.notify(doc.ownerId(), "COMPLIANCE", doc.title() + " expires in " + days + " day" + (days == 1 ? "" : "s"),
                        "Renew it and upload the new version to your document vault.", "vault");
                sent++;
            }
        }
        if (sent > 0) log.info("Compliance reminders sent: {}", sent);
    }
}
