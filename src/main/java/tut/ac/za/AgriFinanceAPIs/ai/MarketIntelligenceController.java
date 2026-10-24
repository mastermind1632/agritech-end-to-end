package tut.ac.za.AgriFinanceAPIs.ai;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/market-intelligence")
public class MarketIntelligenceController {
    private final RestClient gateway;

    public MarketIntelligenceController(RestClient.Builder builder, @Value("${ai.segmentation-url}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(30));
        gateway = builder.baseUrl(url).requestFactory(factory).build();
    }

    @GetMapping("/{view:options|profile|recommendations|demand}")
    public Object get(@PathVariable String view, @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        return proxy(() -> gateway.get().uri("/api/" + view).header(HttpHeaders.AUTHORIZATION, bearer).retrieve().body(Object.class));
    }

    @PutMapping("/profile")
    public Object profile(@RequestBody Map<String, Object> body, @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        return proxy(() -> gateway.put().uri("/api/profile").header(HttpHeaders.AUTHORIZATION, bearer).body(body).retrieve().body(Object.class));
    }

    @DeleteMapping("/profile")
    public Object deleteProfile(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        return proxy(() -> gateway.delete().uri("/api/profile").header(HttpHeaders.AUTHORIZATION, bearer).retrieve().body(Object.class));
    }

    @GetMapping("/records")
    public Object records(@RequestParam(required=false) String crop, @RequestParam(required=false) String province,
            @RequestParam(defaultValue="offer") String kind, @RequestParam(defaultValue="false") boolean mine,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        return proxy(() -> gateway.get().uri(uri -> uri.path("/api/records").queryParam("kind", kind).queryParam("mine", mine)
                .queryParamIfPresent("crop", java.util.Optional.ofNullable(crop))
                .queryParamIfPresent("province", java.util.Optional.ofNullable(province)).build())
                .header(HttpHeaders.AUTHORIZATION, bearer).retrieve().body(Object.class));
    }

    @PostMapping("/{action:records|feedback}")
    public ResponseEntity<Object> create(@PathVariable String action, @RequestBody Map<String, Object> body,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        var result = proxy(() -> gateway.post().uri("/api/" + action).header(HttpHeaders.AUTHORIZATION, bearer)
                .body(body).retrieve().toEntity(Object.class));
        return ResponseEntity.status(result.getStatusCode()).body(result.getBody());
    }

    @DeleteMapping("/records/{id}")
    public Object deleteRecord(@PathVariable UUID id, @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        return proxy(() -> gateway.delete().uri("/api/records/" + id).header(HttpHeaders.AUTHORIZATION, bearer).retrieve().body(Object.class));
    }

    private <T> T proxy(java.util.function.Supplier<T> call) {
        try { return call.get(); }
        catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status != 400 && status != 401 && status != 403 && status != 404 && status != 409 && status != 422 && status != 429) status = 502;
            // Preserve useful status codes without forwarding credentials or internal database errors.
            String message = status == 409 ? "Complete your production profile, enable consent where required, or remove an old listing."
                    : status == 422 ? "Check crop, location, positive quantities and availability dates."
                    : status == 404 ? "Your market record was not found."
                    : "Market intelligence is unavailable. Please retry.";
            throw new ResponseStatusException(HttpStatus.valueOf(status), message, e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Market intelligence is unavailable. Please retry.", e);
        }
    }
}
