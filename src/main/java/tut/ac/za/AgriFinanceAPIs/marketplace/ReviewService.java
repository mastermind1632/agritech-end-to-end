package tut.ac.za.AgriFinanceAPIs.marketplace;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;
import tut.ac.za.AgriFinanceAPIs.marketplace.ReviewDtos.*;
import tut.ac.za.AgriFinanceAPIs.supplier.SupplierRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    public record Agg(Double average, long count) { }

    private static final Set<String> TYPES = Set.of("FARMER", "PRODUCT", "SUPPLIER");

    private final MarketReviewRepository reviews;
    private final MarketOrderRepository orders;
    private final FarmerRepository farmers;
    private final SupplierRepository suppliers;

    public ReviewService(MarketReviewRepository reviews, MarketOrderRepository orders,
                         FarmerRepository farmers, SupplierRepository suppliers) {
        this.reviews = reviews;
        this.orders = orders;
        this.farmers = farmers;
        this.suppliers = suppliers;
    }

    /** Buyer rates the farmer (required) and the product (optional) after a completed order. One review per order. */
    @Transactional
    public void reviewOrder(Long orderId, String reviewerId, OrderReview req) {
        MarketOrder o = orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!reviewerId.equals(o.getBuyerId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        if (!"COMPLETED".equals(o.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "You can rate an order once it is completed");
        if (req == null) throw bad("Rating details are required");
        int farmerStars = stars(req.farmerRating(), "Choose a star rating for the farmer");
        Integer productStars = req.productRating() == null ? null : stars(req.productRating(), "Product rating must be 1 to 5 stars");
        String comment = clean(req.comment());
        if (reviews.existsByOrderIdAndTargetType(orderId, "FARMER")) throw new ResponseStatusException(HttpStatus.CONFLICT, "You already rated this order");

        reviews.save(build("FARMER", o.getSellerId(), reviewerId, orderId, farmerStars, comment));
        if (productStars != null) reviews.save(build("PRODUCT", String.valueOf(o.getListingId()), reviewerId, orderId, productStars, comment));
    }

    /** A farmer who joined a group order with this supplier can rate them. Rating again updates the earlier one. */
    @Transactional
    public void reviewSupplier(String supplierId, String reviewerId, SupplierReview req) {
        if (!suppliers.existsById(supplierId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found");
        if (req == null) throw bad("Rating details are required");
        int s = stars(req.rating(), "Choose a star rating");
        if (reviews.countGroupPurchases(reviewerId, supplierId) == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can rate a supplier after you join one of their group orders");
        }
        MarketReview r = reviews.findByReviewerIdAndTargetTypeAndTargetId(reviewerId, "SUPPLIER", supplierId)
                .orElseGet(() -> build("SUPPLIER", supplierId, reviewerId, null, s, null));
        r.setRating(s);
        r.setComment(clean(req.comment()));
        reviews.save(r);
    }

    @Transactional(readOnly = true)
    public ReviewSummary summary(String type, String id, String viewerId) {
        String t = normalize(type);
        List<MarketReview> list = reviews.findTop20ByTargetTypeAndTargetIdOrderByCreatedAtDesc(t, id);
        Agg agg = aggregates(t, List.of(id)).get(id);
        Map<String, Farmer> people = farmers.findAllById(list.stream().map(MarketReview::getReviewerId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Farmer::getId, f -> f));
        List<ReviewView> views = list.stream().map(r -> new ReviewView(r.getRating(), r.getComment(),
                firstName(people.get(r.getReviewerId())), r.getCreatedAt())).toList();
        Boolean canRate = "SUPPLIER".equals(t) ? reviews.countGroupPurchases(viewerId, id) > 0 : null;
        return new ReviewSummary(agg == null ? null : agg.average(), agg == null ? 0 : agg.count(), views, canRate);
    }

    @Transactional(readOnly = true)
    public Map<String, RatingView> ratings(String type, Collection<String> ids) {
        Map<String, RatingView> out = new LinkedHashMap<>();
        aggregates(normalize(type), ids).forEach((k, v) -> out.put(k, new RatingView(v.average(), v.count())));
        return out;
    }

    /** Average and count for many targets with a single query. */
    public Map<String, Agg> aggregates(String type, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<String, Agg> out = new HashMap<>();
        for (Object[] row : reviews.aggregate(type, ids)) {
            Double avg = row[1] == null ? null : Math.round(((Number) row[1]).doubleValue() * 10.0) / 10.0;
            out.put((String) row[0], new Agg(avg, ((Number) row[2]).longValue()));
        }
        return out;
    }

    private MarketReview build(String type, String targetId, String reviewer, Long orderId, int rating, String comment) {
        MarketReview r = new MarketReview();
        r.setTargetType(type);
        r.setTargetId(targetId);
        r.setReviewerId(reviewer);
        r.setOrderId(orderId);
        r.setRating(rating);
        r.setComment(comment);
        return r;
    }

    private static int stars(Integer v, String message) {
        if (v == null || v < 1 || v > 5) throw bad(message);
        return v;
    }

    private static String clean(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        if (t.length() > 500) throw bad("Comment is too long (max 500 characters)");
        return t;
    }

    private static String normalize(String type) {
        String t = type == null ? "" : type.trim().toUpperCase();
        if (!TYPES.contains(t)) throw bad("Unknown rating type");
        return t;
    }

    private static String firstName(Farmer f) {
        if (f == null || f.getName() == null || f.getName().isBlank()) return "Farmer";
        return f.getName().trim().split("\\s+")[0];
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
