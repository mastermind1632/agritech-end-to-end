package tut.ac.za.AgriFinanceAPIs.recommendation;
import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*; import org.springframework.security.core.Authentication; import tut.ac.za.AgriFinanceAPIs.recommendation.dto.RecommendationRequest; import java.util.List;
@RestController @RequestMapping("/api/recommendations")
public class RecommendationController {
 private final RecommendationService service; public RecommendationController(RecommendationService s){service=s;}
 @PostMapping("/generate") @ResponseStatus(HttpStatus.CREATED) public List<AiRecommendation> generate(@RequestBody RecommendationRequest r, Authentication auth){r.setFarmerId(auth.getName()); return service.generate(r.getFarmerId());}
 @GetMapping("/farmer/{farmerId}") public List<AiRecommendation> forFarmer(@PathVariable String farmerId, Authentication auth){if(!farmerId.equals(auth.getName())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"You can only access your own recommendations"); return service.forFarmer(farmerId);}
}
