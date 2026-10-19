package tut.ac.za.AgriFinanceAPIs.compliance;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compliance")
public class ComplianceReminderController {

    private final ComplianceReminderService service;

    public ComplianceReminderController(ComplianceReminderService service) {
        this.service = service;
    }

    @GetMapping("/{farmerId}/reminders")
    public List<ComplianceReminder> list(@PathVariable String farmerId) {
        return service.listForFarmer(farmerId);
    }

    @PostMapping("/{farmerId}/reminders/regenerate")
    public Map<String, Object> regenerate(@PathVariable String farmerId) {
        List<ComplianceReminder> created = service.regenerateForFarmer(farmerId);
        return Map.of(
            "farmerId", farmerId,
            "remindersCreated", created.size(),
            "reminders", created
        );
    }
}
