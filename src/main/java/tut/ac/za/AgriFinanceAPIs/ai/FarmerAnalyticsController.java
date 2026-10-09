package tut.ac.za.AgriFinanceAPIs.ai;

import tut.ac.za.AgriFinanceAPIs.ai.AnalyticsDtos.AnomalyReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class FarmerAnalyticsController {
    private final FarmerAnalyticsService service;

    public FarmerAnalyticsController(FarmerAnalyticsService service) {
        this.service = service;
    }

    @GetMapping("/farmer-clusters")
    public BuyingGroup clusters(@RequestParam(defaultValue = "3") int k, Authentication auth) {
        var clusters = service.clusters(Math.max(1, Math.min(k, 10)));
        for (var cluster : clusters.clusters()) {
            if (cluster.farmers().stream().anyMatch(member -> member.farmer().farmerId().equals(auth.getName()))) {
                return new BuyingGroup(Math.max(0, cluster.farmers().size() - 1));
            }
        }
        return new BuyingGroup(0);
    }

    @GetMapping("/farmers/{farmerId}/nearest")
    public List<SimilarFarmer> nearestFarmers(
            @PathVariable String farmerId,
            @RequestParam(defaultValue = "5") int limit, Authentication auth) {
        requireOwner(farmerId, auth);
        return service.nearestFarmers(farmerId, Math.max(1, Math.min(limit, 20))).matches().stream()
                .map(match -> new SimilarFarmer(match.similarity(), match.reasons())).toList();
    }

    @GetMapping("/farmers/{farmerId}/anomalies")
    public AnomalyReport anomalies(@PathVariable String farmerId, Authentication auth) {
        requireOwner(farmerId, auth);
        return service.anomalies(farmerId);
    }

    private void requireOwner(String farmerId, Authentication auth) {
        if (!farmerId.equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own farm insights");
        }
    }

    public record BuyingGroup(int otherFarmers) {}
    public record SimilarFarmer(double similarity, List<String> reasons) {}
}
