package tut.ac.za.AgriFinanceAPIs.recommendation;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.recommendation.dto.RecommendationRequest;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final RecommendationService service;
    public RecommendationController(RecommendationService service) { this.service = service; }
    @PostMapping("/generate")
    public List<RecommendationService.Opportunity> generate(@RequestBody RecommendationRequest request, Authentication auth) {
        return service.generate(auth.getName());
    }
    @GetMapping("/farmer/{farmerId}")
    public List<RecommendationService.Opportunity> forFarmer(@PathVariable String farmerId, Authentication auth) {
        if (!farmerId.equals(auth.getName()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own recommendations");
        return service.forFarmer(farmerId);
    }
    @GetMapping("/{orderId}/quote")
    public RecommendationService.Opportunity quote(@PathVariable String orderId,
            @RequestParam(defaultValue="1") int quantity, Authentication auth) {
        return service.quote(auth.getName(), orderId, quantity);
    }
}
