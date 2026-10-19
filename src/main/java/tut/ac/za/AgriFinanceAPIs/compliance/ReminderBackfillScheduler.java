package tut.ac.za.AgriFinanceAPIs.compliance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReminderBackfillScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderBackfillScheduler.class);

    private final ComplianceReminderRepository reminders;
    private final ComplianceReminderService service;

    public ReminderBackfillScheduler(ComplianceReminderRepository reminders,
                                     ComplianceReminderService service) {
        this.reminders = reminders;
        this.service = service;
    }

    @Scheduled(cron = "0 30 6 * * *")
    public void backfillMissing() {
        List<String> missing = reminders.findFarmerIdsWithNoReminders();
        if (missing.isEmpty()) {
            log.info("Reminder backfill: no farmers missing reminders");
            return;
        }
        int generated = 0;
        for (String farmerId : missing) {
            try {
                service.regenerateForFarmer(farmerId);
                generated++;
            } catch (Exception ex) {
                log.warn("Reminder backfill failed for farmer {}: {}", farmerId, ex.getMessage());
            }
        }
        log.info("Reminder backfill: generated reminders for {} of {} farmers", generated, missing.size());
    }
}
