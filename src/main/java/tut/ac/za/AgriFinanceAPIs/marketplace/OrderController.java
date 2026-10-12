package tut.ac.za.AgriFinanceAPIs.marketplace;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tut.ac.za.AgriFinanceAPIs.marketplace.OrderDtos.*;

import java.util.List;

@RestController
@RequestMapping("/api/market/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /** The buyer is always the logged-in user; it is never read from the request body. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView place(@RequestBody PlaceOrder request, Authentication auth) {
        return service.place(auth.getName(), request);
    }

    @GetMapping("/buying")
    public List<OrderView> buying(Authentication auth) { return service.buying(auth.getName()); }

    @GetMapping("/selling")
    public List<OrderView> selling(Authentication auth) { return service.selling(auth.getName()); }

    @GetMapping("/sales-summary")
    public SalesSummary salesSummary(Authentication auth) { return service.salesSummary(auth.getName()); }

    @GetMapping("/{id}")
    public OrderView get(@PathVariable Long id, Authentication auth) {
        return service.get(id, auth.getName(), isAdmin(auth));
    }

    @PostMapping("/{id}/confirm")
    public OrderView confirm(@PathVariable Long id, Authentication auth) { return service.confirm(id, auth.getName()); }

    @PostMapping("/{id}/decline")
    public OrderView decline(@PathVariable Long id, @RequestBody(required = false) Reason body, Authentication auth) {
        return service.decline(id, auth.getName(), body == null ? null : body.reason());
    }

    @PostMapping("/{id}/cancel")
    public OrderView cancel(@PathVariable Long id, @RequestBody(required = false) Reason body, Authentication auth) {
        return service.cancel(id, auth.getName(), body == null ? null : body.reason());
    }

    @PostMapping("/{id}/dispatch")
    public OrderView dispatch(@PathVariable Long id, Authentication auth) { return service.dispatch(id, auth.getName()); }

    @PostMapping("/{id}/deliver")
    public OrderView deliver(@PathVariable Long id, @RequestBody Deliver body, Authentication auth) {
        return service.deliver(id, auth.getName(), body);
    }

    @PostMapping("/{id}/confirm-payment")
    public OrderView confirmPayment(@PathVariable Long id, Authentication auth) {
        return service.confirmPayment(id, auth.getName());
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
