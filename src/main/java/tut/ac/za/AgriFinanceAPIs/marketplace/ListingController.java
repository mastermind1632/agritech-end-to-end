package tut.ac.za.AgriFinanceAPIs.marketplace;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/market/listings")
public class ListingController {

    private final ListingService service;

    public ListingController(ListingService service) {
        this.service = service;
    }

    @GetMapping
    public List<ListingResponse> search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
            @RequestParam(name = "availableOn", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate availableOn,
            @RequestParam(name = "lat", required = false) Double lat,
            @RequestParam(name = "lng", required = false) Double lng,
            @RequestParam(name = "radiusKm", required = false) Double radiusKm,
            Authentication auth) {
        return service.search(new ListingService.Filters(q, category, minPrice, maxPrice, availableOn, lat, lng, radiusKm), auth.getName());
    }

    @GetMapping("/mine")
    public List<ListingResponse> mine(Authentication auth) {
        return service.mine(auth.getName());
    }

    @GetMapping("/{id}")
    public ListingResponse get(@PathVariable Long id, Authentication auth) {
        return service.get(id, auth.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse create(@RequestBody ListingRequest request, Authentication auth) {
        return service.create(auth.getName(), request);
    }

    @PutMapping("/{id}")
    public ListingResponse update(@PathVariable Long id, @RequestBody ListingRequest request, Authentication auth) {
        return service.update(id, request, auth.getName(), isAdmin(auth));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id, Authentication auth) {
        service.remove(id, auth.getName(), isAdmin(auth));
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
