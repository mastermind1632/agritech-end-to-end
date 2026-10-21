package tut.ac.za.AgriFinanceAPIs.insights;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InsightsController {

    private final InsightsService service;

    public InsightsController(InsightsService service) {
        this.service = service;
    }

    @GetMapping("/api/insights/overview")
    public InsightsService.Overview overview(Authentication auth) {
        return service.overview(auth.getName());
    }
}
