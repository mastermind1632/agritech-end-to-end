package tut.ac.za.AgriFinanceAPIs.compliance.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tut.ac.za.AgriFinanceAPIs.compliance.ComplianceReminder;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;

@Service
public class ReminderDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(ReminderDeliveryService.class);

    private final FarmerRepository farmers;
    private final SmsProvider smsProvider;

    public ReminderDeliveryService(FarmerRepository farmers, SmsProvider smsProvider) {
        this.farmers = farmers;
        this.smsProvider = smsProvider;
    }

    /**
     * Attempt to deliver a single reminder via its channel.
     * Returns true if delivery succeeded, false otherwise (in which case the caller
     * should leave the reminder as sent=false so the next scheduler tick retries).
     */
    public boolean deliver(ComplianceReminder reminder) {
        String phone = resolvePhone(reminder.getFarmerId());
        if (phone == null || phone.isBlank()) {
            log.warn("Reminder {} for farmer {} skipped: no phone number on record",
                    reminder.getId(), reminder.getFarmerId());
            return false;
        }
        String message = composeMessage(reminder);
        if ("IN_APP".equalsIgnoreCase(reminder.getChannel())) {
            // In-app delivery is out of scope for SMS; log only, treat as delivered.
            log.info("[IN_APP] farmer={} reminder={} message={}",
                    reminder.getFarmerId(), reminder.getId(), message);
            return true;
        }
        return smsProvider.send(phone, message);
    }

    private String resolvePhone(String farmerId) {
        if (farmerId == null) return null;
        return farmers.findById(farmerId).map(Farmer::getPhoneNumber).orElse(null);
    }

    private String composeMessage(ComplianceReminder reminder) {
        String type = reminder.getReminderType() == null ? "deadline"
                : reminder.getReminderType().replace('_', ' ').toLowerCase();
        return "AgriTech reminder: " + type + " due on " + reminder.getDueDate()
                + " (in " + reminder.getDaysBefore() + " day(s)). Reply STOP to opt out.";
    }
}
