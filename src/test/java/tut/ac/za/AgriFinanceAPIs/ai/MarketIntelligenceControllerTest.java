package tut.ac.za.AgriFinanceAPIs.ai;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class MarketIntelligenceControllerTest {
    private HttpServer server;
    private MarketIntelligenceController controller;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private int status = 200;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().toString());
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"profile\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        controller = new MarketIntelligenceController(RestClient.builder(), "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void forwardsTheBearerAndUsesFixedServicePaths() {
        controller.get("profile", "Bearer test-token");
        assertEquals("Bearer test-token", authorization.get());
        assertEquals("/api/profile", path.get());
        controller.profile(Map.of("crop", "tomato"), "Bearer test-token");
        assertTrue(requestBody.get().contains("tomato"));
        assertEquals("/api/profile", path.get());
    }

    @Test
    void encodesFiltersAndUsesTypedListingIds() {
        controller.records("tomato", "Western Cape", "demand", true, "Bearer test-token");
        assertTrue(path.get().contains("Western%20Cape"));
        assertTrue(path.get().contains("mine=true"));
        var id = UUID.randomUUID();
        controller.deleteRecord(id, "Bearer test-token");
        assertEquals("/api/records/" + id, path.get());
    }

    @Test
    void preservesValidationAndOwnershipStatusesWithoutForwardingInternalDetails() {
        status = 422;
        var error = assertThrows(ResponseStatusException.class, () -> controller.profile(Map.of(), "Bearer test-token"));
        assertEquals(422, error.getStatusCode().value());
        assertTrue(error.getReason().contains("Check crop"));
        status = 404;
        var missing = assertThrows(ResponseStatusException.class, () -> controller.deleteRecord(UUID.randomUUID(), "Bearer test-token"));
        assertEquals(404, missing.getStatusCode().value());
    }
}
