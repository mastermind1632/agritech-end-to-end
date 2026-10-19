package tut.ac.za.AgriFinanceAPIs.compliance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ComplianceReminderService service;

    public ReminderScheduler(ComplianceReminderService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 6 * * *")
    public void dispatchDaily() {
        int dispatched = service.dispatchDue();
        log.info("Compliance reminder scheduler dispatched {} reminder(s)", dispatched);
    }
}
