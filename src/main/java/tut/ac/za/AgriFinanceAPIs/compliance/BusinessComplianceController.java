package tut.ac.za.AgriFinanceAPIs.compliance;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/compliance")
public class BusinessComplianceController {

    private final BusinessComplianceRepository repository;
    private final ComplianceReminderService reminderService;

    public BusinessComplianceController(BusinessComplianceRepository repository,
                                        ComplianceReminderService reminderService) {
        this.repository = repository;
        this.reminderService = reminderService;
    }

    @GetMapping("/{farmerId}")
    public Map<String, Object> getComplianceChecklist(@PathVariable String farmerId, Authentication authentication) {
        requireOwner(farmerId, authentication);
        BusinessCompliance compliance = repository.findByFarmerId(farmerId)
                .orElseGet(() -> {
                    BusinessCompliance bc = new BusinessCompliance();
                    bc.setFarmerId(farmerId);
                    return repository.save(bc);
                });

        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(Map.of("stage", "Register the business", "authority", "BizPortal and CIPC",
                "completed", compliance.isCompanyRegistered(), "source", "https://www.cipc.co.za"));
        tasks.add(Map.of("stage", "Income Tax & eFiling Registration", "authority", "SARS",
                "completed", compliance.isTaxRegistered(), "source", "https://www.sars.gov.za"));
        tasks.add(Map.of("stage", "VAT Registration & Diesel Refund Check", "authority", "SARS",
                "completed", compliance.isVatRegistered(), "source", "https://www.sarsefiling.co.za"));

        if (compliance.isHasEmployees()) {
            tasks.add(Map.of("stage", "UIF & Compensation Fund (COIDA) Registration",
                    "authority", "Dept. of Employment and Labour",
                    "completed", false, "source", "https://www.ufiling.co.za"));
        }

        long completedCount = tasks.stream().filter(t -> (Boolean) t.get("completed")).count();
        int progressScore = tasks.isEmpty() ? 0 : (int) ((completedCount * 100) / tasks.size());

        return Map.of("compliance", compliance, "checklist", tasks, "progressScore", progressScore);
    }

    @PostMapping("/{farmerId}")
    public BusinessCompliance updateCompliance(@PathVariable String farmerId, @RequestBody BusinessCompliance update,
                                               Authentication authentication) {
        requireOwner(farmerId, authentication);
        BusinessCompliance compliance = repository.findByFarmerId(farmerId)
                .orElseGet(() -> {
                    BusinessCompliance bc = new BusinessCompliance();
                    bc.setFarmerId(farmerId);
                    return bc;
                });
        compliance.setCompanyRegistered(update.isCompanyRegistered());
        compliance.setCipcRegistrationNumber(update.getCipcRegistrationNumber());
        compliance.setTaxRegistered(update.isTaxRegistered());
        compliance.setVatRegistered(update.isVatRegistered());
        compliance.setHasEmployees(update.isHasEmployees());
        compliance.setUpdatedAt(LocalDateTime.now());
        BusinessCompliance saved = repository.save(compliance);

        try {
            reminderService.regenerateForFarmer(farmerId);
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(BusinessComplianceController.class)
                .warn("Could not regenerate reminders for {}: {}", farmerId, ex.getMessage());
        }

        return saved;
    }

    private void requireOwner(String farmerId, Authentication authentication) {
        if (!farmerId.equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only access your own compliance details");
        }
    }
}
