package tut.ac.za.AgriFinanceAPIs.compliance;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    public List<ComplianceReminder> list(@PathVariable String farmerId, Authentication authentication) {
        requireOwner(farmerId, authentication);
        return service.listForFarmer(farmerId);
    }

    @PostMapping("/{farmerId}/reminders/regenerate")
    public Map<String, Object> regenerate(@PathVariable String farmerId, Authentication authentication) {
        requireOwner(farmerId, authentication);
        List<ComplianceReminder> created = service.regenerateForFarmer(farmerId);
        return Map.of(
            "farmerId", farmerId,
            "remindersCreated", created.size(),
            "reminders", created
        );
    }

    private void requireOwner(String farmerId, Authentication authentication) {
        if (!farmerId.equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only access your own compliance reminders");
        }
    }
}
