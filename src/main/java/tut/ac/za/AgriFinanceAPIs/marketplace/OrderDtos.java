package tut.ac.za.AgriFinanceAPIs.marketplace;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() { }

    public record PlaceOrder(Long listingId, BigDecimal quantity, String note) { }

    public record Deliver(String code, Double latitude, Double longitude) { }

    public record Reason(String reason) { }

    public record EventView(String actorName, String fromStatus, String toStatus, String note, LocalDateTime at) { }

    /** costTotal and profit are only filled in for the selling farmer. */
    public record OrderView(
            Long id, Long listingId, String product, String unit,
            BigDecimal pricePerUnit, BigDecimal quantity, BigDecimal totalAmount,
            String status, String myRole,
            String buyerId, String buyerName, String buyerContact,
            String sellerId, String sellerName, String sellerContact,
            String deliveryNote, String deliveryCode,
            boolean buyerConfirmedPaid, boolean sellerConfirmedPaid,
            List<String> allowedActions,
            LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deliveredAt, LocalDateTime completedAt,
            Long imageId, BigDecimal costTotal, BigDecimal profit, boolean reviewed, boolean canReview,
            List<EventView> events,
            Integer buyerReliability, Long openDisputeId, Long latestDisputeId) { }

    public record SalesSummary(long completedOrders, long pendingOrders, BigDecimal revenue,
                               BigDecimal knownCost, BigDecimal profit, long ordersMissingCost) { }
}
