package tut.ac.za.AgriFinanceAPIs.compliance;

import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/compliance")
public class BusinessComplianceController {

    private final BusinessComplianceRepository repository;

    public BusinessComplianceController(BusinessComplianceRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{farmerId}")
    public Map<String, Object> getComplianceChecklist(@PathVariable String farmerId) {
        BusinessCompliance compliance = repository.findByFarmerId(farmerId)
                .orElseGet(() -> {
                    BusinessCompliance bc = new BusinessCompliance();
                    bc.setFarmerId(farmerId);
                    return repository.save(bc);
                });

        List<Map<String, Object>> tasks = new ArrayList<>();
        tasks.add(Map.of(
                "stage", "Register the business",
                "authority", "BizPortal and CIPC",
                "completed", compliance.isCompanyRegistered(),
                "source", "https://www.cipc.co.za"
        ));
        tasks.add(Map.of(
                "stage", "Income Tax & eFiling Registration",
                "authority", "SARS",
                "completed", compliance.isTaxRegistered(),
                "source", "https://www.sars.gov.za"
        ));
        tasks.add(Map.of(
                "stage", "VAT Registration & Diesel Refund Check",
                "authority", "SARS",
                "completed", compliance.isVatRegistered(),
                "source", "https://www.sarsefiling.co.za"
        ));

        if (compliance.isHasEmployees()) {
            tasks.add(Map.of(
                    "stage", "UIF & Compensation Fund (COIDA) Registration",
                    "authority", "Dept. of Employment and Labour",
                    "completed", false,
                    "source", "https://www.ufiling.co.za"
            ));
        }

        long completedCount = tasks.stream().filter(t -> (Boolean) t.get("completed")).count();
        int progressScore = tasks.isEmpty() ? 0 : (int) ((completedCount * 100) / tasks.size());

        return Map.of(
                "compliance", compliance,
                "checklist", tasks,
                "progressScore", progressScore
        );
    }

    @PostMapping("/{farmerId}")
    public BusinessCompliance updateCompliance(@PathVariable String farmerId, @RequestBody BusinessCompliance update) {
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
        return repository.save(compliance);
    }
}
