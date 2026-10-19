package tut.ac.za.AgriFinanceAPIs.ai;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/segmentation")
public class SegmentationProxyController {

    private final SegmentationClient client;

    public SegmentationProxyController(SegmentationClient client) {
        this.client = client;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        try {
            return ok(client.get("/health", ""));
        } catch (RestClientResponseException ex) {
            return relay(ex);
        }
    }

    @GetMapping("/options")
    public ResponseEntity<String> options(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        try {
            return ok(client.get("/api/options", bearer));
        } catch (RestClientResponseException ex) {
            return relay(ex);
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<String> getProfile(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        try {
            return ok(client.get("/api/profile", bearer));
        } catch (RestClientResponseException ex) {
            return relay(ex);
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<String> saveProfile(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
                                              @RequestBody String body) {
        try {
            return ok(client.put("/api/profile", bearer, body));
        } catch (RestClientResponseException ex) {
            return relay(ex);
        }
    }

    @DeleteMapping("/profile")
    public ResponseEntity<String> deleteProfile(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        try {
            return ok(client.delete("/api/profile", bearer));
        } catch (RestClientResponseException ex) {
            return relay(ex);
        }
    }

    @GetMapping("/records")
    public ResponseEntity<String> records(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
                                          @RequestParam Map<String, String> query) {
        String path = buildPathWithQuery("/api/records", query);
        try {
            return ok(client.get(path, bearer));
        } catch (RestClientResponseException ex) {
            return relay(ex);
        }
    }

    @PostMapping("/records")
    public ResponseEntity<String> addRecord(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
                                            @RequestBody String body) {
        try {
            return ok(client.post("/api/records", bearer, body));
        } catch (RestClientResponseException ex) {
            return relay(ex);
        }
    }

    @DeleteMapping("/records/{id}")
    public ResponseEntity<String> removeRecord(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
                                               @PathVariable String id) {
        try {
            return ok(client.delete("/api/records/" + id, bearer));
        } catch (RestClientResponseException ex) {
            return relay(ex);
        }
    }

    @GetMapping("/recommendations")
    public ResponseEntity<String> recommendations(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        try {
            return ok(client.get("/api/recommendations", bearer));
        } catch (RestClientResponseException ex) {
            return relay(ex);
        }
    }

    @PostMapping("/feedback")
    public ResponseEntity<String> feedback(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
                                           @RequestBody String body) {
        try {
            return ok(client.post("/api/feedback", bearer, body));
        } catch (RestClientResponseException ex) {
            return relay(ex);
        }
    }

    @GetMapping("/demand")
    public ResponseEntity<String> demand(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        try {
            return ok(client.get("/api/demand", bearer));
        } catch (RestClientResponseException ex) {
            return relay(ex);
        }
    }

    private ResponseEntity<String> ok(String body) {
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private ResponseEntity<String> relay(RestClientResponseException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ex.getResponseBodyAsString());
    }

    private String buildPathWithQuery(String path, Map<String, String> query) {
        if (query == null || query.isEmpty()) return path;
        StringBuilder sb = new StringBuilder(path).append('?');
        boolean first = true;
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (!first) sb.append('&');
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }
}
