package tut.ac.za.AgriFinanceAPIs.compliance;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.compliance.delivery.ReminderDeliveryService;

import java.util.Map;

@RestController
@RequestMapping("/api/compliance")
public class ReminderDeliveryController {

    private final ComplianceReminderRepository reminders;
    private final ReminderDeliveryService delivery;

    public ReminderDeliveryController(ComplianceReminderRepository reminders,
                                      ReminderDeliveryService delivery) {
        this.reminders = reminders;
        this.delivery = delivery;
    }

    @PostMapping("/{farmerId}/reminders/{reminderId}/send-now")
    public ResponseEntity<Map<String, Object>> sendNow(@PathVariable String farmerId,
                                                       @PathVariable Long reminderId) {
        ComplianceReminder r = reminders.findById(reminderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Reminder not found: " + reminderId));
        if (!farmerId.equals(r.getFarmerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Reminder does not belong to this farmer");
        }
        boolean ok = delivery.deliver(r);
        if (ok) {
            r.setSent(true);
            reminders.save(r);
        }
        return ResponseEntity.ok(Map.of(
                "reminderId", reminderId,
                "farmerId", farmerId,
                "delivered", ok,
                "sent", r.isSent()
        ));
    }
}
