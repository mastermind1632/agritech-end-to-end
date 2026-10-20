package tut.ac.za.AgriFinanceAPIs.consent;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/consent")
public class ConsentController {

    private final ConsentService service;

    public ConsentController(ConsentService service) {
        this.service = service;
    }

    public record ConsentRequest(String type, boolean granted, String version) {}

    @GetMapping("/{farmerId}")
    public Map<String, Boolean> current(@PathVariable String farmerId) {
        return service.currentState(farmerId);
    }

    @GetMapping("/{farmerId}/history")
    public List<ConsentRecord> history(@PathVariable String farmerId) {
        return service.history(farmerId);
    }

    @GetMapping("/{farmerId}/check")
    public Map<String, Object> check(@PathVariable String farmerId, @RequestParam String type) {
        boolean has = service.hasConsent(farmerId, type);
        return Map.of("farmerId", farmerId, "type", type, "granted", has);
    }

    @PostMapping("/{farmerId}")
    public ResponseEntity<ConsentRecord> record(@PathVariable String farmerId,
                                                 @RequestBody ConsentRequest req,
                                                 HttpServletRequest http) {
        if (req.type() == null || req.type().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consent type required");
        }
        String ip = http.getRemoteAddr();
        ConsentRecord saved = service.record(farmerId, req.type(), req.granted(), req.version(), ip);
        return ResponseEntity.ok(saved);
    }
}
