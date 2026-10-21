package tut.ac.za.AgriFinanceAPIs.marketplace;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class DisputeDtos {
    private DisputeDtos() { }

    public record RaiseDispute(String reason, String description) { }

    public record Respond(String response) { }

    public record Resolve(String resolution, String note) { }

    public record DisputeView(Long id, Long orderId, String product, BigDecimal orderTotal, String reason, String reasonLabel,
                              String description, String status, String previousStatus,
                              String raisedById, String raisedByName, String againstId, String againstName,
                              String myRole, LocalDateTime respondBy, String response, LocalDateTime respondedAt,
                              String resolution, String resolutionNote, LocalDateTime resolvedAt, LocalDateTime createdAt,
                              List<Map<String, Object>> evidence, List<String> allowedActions) { }
}
