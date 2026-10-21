package tut.ac.za.AgriFinanceAPIs.marketplace;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import tut.ac.za.AgriFinanceAPIs.common.PageResult;
import tut.ac.za.AgriFinanceAPIs.common.Roles;

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
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "minRating", required = false) Double minRating,
            @RequestParam(name = "verifiedOnly", defaultValue = "false") boolean verifiedOnly,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            Authentication auth, HttpServletResponse response) {
        List<ListingResponse> all = service.search(new ListingService.Filters(q, category, minPrice, maxPrice, availableOn, lat, lng, radiusKm),
                new ListingService.Sorting(sort, minRating, verifiedOnly), auth.getName());
        // Total for "showing 24 of 130"; without page/size the full list is returned (backwards compatible).
        response.setHeader("X-Total-Count", String.valueOf(all.size()));
        response.setHeader("Access-Control-Expose-Headers", "X-Total-Count");
        if (page == null && size == null) return all;
        return PageResult.slice(all, page == null ? 0 : page, size == null ? 24 : size).items();
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
        Roles.require(auth, Roles.FARMER);
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
