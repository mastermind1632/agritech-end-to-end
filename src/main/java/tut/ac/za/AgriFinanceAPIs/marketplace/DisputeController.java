package tut.ac.za.AgriFinanceAPIs.marketplace;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.Roles;
import tut.ac.za.AgriFinanceAPIs.marketplace.DisputeDtos.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class DisputeController {

    private final DisputeService service;

    public DisputeController(DisputeService service) {
        this.service = service;
    }

    @GetMapping("/api/market/disputes/reasons")
    public Map<String, String> reasons() {
        return DisputeService.REASONS;
    }

    @PostMapping("/api/market/orders/{orderId}/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    public DisputeView raise(@PathVariable Long orderId, @RequestBody RaiseDispute body, Authentication auth) {
        return service.raise(orderId, auth.getName(), body);
    }

    @GetMapping("/api/market/disputes")
    public List<DisputeView> mine(Authentication auth) {
        return service.mine(auth.getName());
    }

    @GetMapping("/api/market/disputes/{id}")
    public DisputeView get(@PathVariable Long id, Authentication auth) {
        return service.get(id, auth.getName(), Roles.isAdmin(auth));
    }

    @PostMapping("/api/market/disputes/{id}/respond")
    public DisputeView respond(@PathVariable Long id, @RequestBody Respond body, Authentication auth) {
        return service.respond(id, auth.getName(), body);
    }

    @PostMapping(value = "/api/market/disputes/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DisputeView evidence(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                @RequestParam(value = "note", required = false) String note, Authentication auth) throws IOException {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded");
        return service.addEvidence(id, auth.getName(), file.getOriginalFilename(), file.getBytes(), note);
    }

    @PostMapping("/api/market/disputes/{id}/withdraw")
    public DisputeView withdraw(@PathVariable Long id, Authentication auth) {
        return service.withdraw(id, auth.getName());
    }

    @GetMapping("/api/admin/disputes")
    public List<DisputeView> all(@RequestParam(defaultValue = "true") boolean openOnly, Authentication auth) {
        return service.all(openOnly, auth.getName());
    }

    @PostMapping("/api/admin/disputes/{id}/resolve")
    public DisputeView resolve(@PathVariable Long id, @RequestBody Resolve body, Authentication auth) {
        return service.resolve(id, auth.getName(), body);
    }
}
