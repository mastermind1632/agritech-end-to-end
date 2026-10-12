package tut.ac.za.AgriFinanceAPIs.marketplace;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tut.ac.za.AgriFinanceAPIs.marketplace.ReviewDtos.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/market/reviews")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    /** Reviewer is always the logged-in user. */
    @PostMapping("/order/{orderId}")
    @ResponseStatus(HttpStatus.CREATED)
    public void reviewOrder(@PathVariable Long orderId, @RequestBody OrderReview body, Authentication auth) {
        service.reviewOrder(orderId, auth.getName(), body);
    }

    @PostMapping("/supplier/{supplierId}")
    @ResponseStatus(HttpStatus.CREATED)
    public void reviewSupplier(@PathVariable String supplierId, @RequestBody SupplierReview body, Authentication auth) {
        service.reviewSupplier(supplierId, auth.getName(), body);
    }

    /** ?type=SUPPLIER&ids=a,b,c gives averages for many targets in one call. */
    @GetMapping("/ratings")
    public Map<String, RatingView> ratings(@RequestParam("type") String type, @RequestParam("ids") String ids) {
        List<String> list = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).limit(200).toList();
        return service.ratings(type, list);
    }

    @GetMapping("/{type}/{id}")
    public ReviewSummary summary(@PathVariable String type, @PathVariable String id, Authentication auth) {
        return service.summary(type, id, auth.getName());
    }
}
