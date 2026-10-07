package tut.ac.za.AgriFinanceAPIs.recommendation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.farmer.*;
import tut.ac.za.AgriFinanceAPIs.finance.*;
import tut.ac.za.AgriFinanceAPIs.grouporder.*;
import tut.ac.za.AgriFinanceAPIs.supplier.*;

@Service
public class RecommendationService {
    public record PriceComparison(String currency, int quantity, BigDecimal listedUnitPrice,
            BigDecimal listedTotal, BigDecimal listedDiscountPercent, BigDecimal indicativeGroupTotal,
            BigDecimal indicativeDifference, boolean supplierDiscountConfirmed,
            boolean deliveryKnown, boolean finalTotalKnown, Instant checkedAt) {}
    public record EquivalentPrice(String productId, String productName, BigDecimal packSize,
            String packUnit, BigDecimal listedPrice, BigDecimal pricePerUnit) {}
    public record Opportunity(String id, String recommendedProductId, String suggestedGroupOrderId,
            String productName, String reason, List<String> matchedItems, int remainingQuantity,
            PriceComparison quote, List<EquivalentPrice> equivalentPrices) {}
    private final FarmerRepository farmers;
    private final ExpenseRepository expenses;
    private final SupplierProductRepository products;
    private final GroupOrderRepository orders;
    private final GroupOrderItemRepository items;

    public RecommendationService(AiRecommendationRepository unused, FarmerRepository farmers,
            ExpenseRepository expenses, SupplierProductRepository products,
            GroupOrderRepository orders, GroupOrderItemRepository items) {
        this.farmers = farmers; this.expenses = expenses; this.products = products;
        this.orders = orders; this.items = items;
    }

    public List<Opportunity> forFarmer(String farmerId) { return generate(farmerId); }

    public List<Opportunity> generate(String farmerId) {
        if (!farmers.existsById(farmerId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found");
        List<String> history = new ArrayList<>(expenses.findAllByFarmerIdOrderByDateDesc(farmerId)
                .stream().map(Expense::getItem).filter(Objects::nonNull).toList());
        // Use only this farmer's purchases, never another farmer's participation as a reason.
        for (GroupOrderItem item : items.findAllByFarmerId(farmerId)) {
            orders.findById(item.getGroupOrderId()).flatMap(o -> products.findById(o.getProductId()))
                    .map(SupplierProduct::getProductName).ifPresent(history::add);
        }
        List<Opportunity> result = new ArrayList<>();
        for (GroupOrder order : orders.findAllByStatusOrderByCreatedAtDesc("open")) {
            if (remaining(order) < 1 || items.existsByGroupOrderIdAndFarmerId(order.getId(), farmerId)) continue;
            SupplierProduct product = products.findById(order.getProductId()).orElse(null);
            if (product == null || product.getProductName() == null || !validPrice(product, order)) continue;
            List<String> matched = history.stream().filter(h -> matches(h, product.getProductName()))
                    .distinct().limit(5).toList();
            if (matched.isEmpty()) continue;
            result.add(new Opportunity(order.getId(), product.getId(), order.getId(), product.getProductName(),
                    "Matches your recorded purchases: " + String.join(", ", matched)
                    + ". Check the variety, pack size and supplier terms before joining.",
                    matched, remaining(order), compare(product, order, 1), equivalents(product)));
        }
        return result.stream().sorted(Comparator.comparingInt((Opportunity o) -> o.matchedItems().size())
                .reversed().thenComparing(Opportunity::id)).limit(10).toList();
    }

    public Opportunity quote(String farmerId, String orderId, int quantity) {
        Opportunity own = generate(farmerId).stream().filter(o -> o.id().equals(orderId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buying opportunity unavailable"));
        GroupOrder order = orders.findById(orderId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Order is no longer available"));
        SupplierProduct product = products.findById(own.recommendedProductId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Product is no longer available"));
        return new Opportunity(own.id(), own.recommendedProductId(), own.suggestedGroupOrderId(),
                own.productName(), own.reason(), own.matchedItems(), remaining(order), compare(product, order, quantity),
                equivalents(product));
    }

    private List<EquivalentPrice> equivalents(SupplierProduct selected) {
        if (!hasSpecification(selected)) return List.of();
        // A shared exact specification code and unit are required. Names alone do not prove equivalence.
        return products.findAll().stream().filter(p -> hasSpecification(p)
                && p.getComparisonKey().equalsIgnoreCase(selected.getComparisonKey())
                && p.getPackUnit().equals(selected.getPackUnit()) && p.getPrice()!=null && p.getPrice().signum()>=0)
                .map(p -> new EquivalentPrice(p.getId(),p.getProductName(),p.getPackSize(),p.getPackUnit(),p.getPrice(),
                        p.getPrice().divide(p.getPackSize(),6,RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(EquivalentPrice::pricePerUnit).thenComparing(EquivalentPrice::productId))
                .limit(6).toList();
    }

    private static boolean hasSpecification(SupplierProduct p) {
        return p.getComparisonKey()!=null && !p.getComparisonKey().isBlank()
                && p.getPackSize()!=null && p.getPackSize().signum()>0
                && Set.of("kg","l","unit").contains(p.getPackUnit()==null?"":p.getPackUnit());
    }

    static boolean matches(String purchase, String product) {
        Set<String> a = terms(purchase), b = terms(product);
        return !a.isEmpty() && !b.isEmpty() && a.stream().anyMatch(b::contains);
    }

    private static Set<String> terms(String text) {
        Set<String> generic = Set.of("seed", "seeds", "fertiliser", "fertilizer", "bag", "bags",
                "kg", "pack", "packs", "the", "for", "and", "purchase", "bought", "buy", "of", "litre", "litres",
                "planting", "agricultural", "organic", "hybrid", "farm", "farming", "supply", "supplies");
        Set<String> result = new HashSet<>();
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z]+"))
            if (token.length() > 2 && !generic.contains(token)) result.add(token);
        return result;
    }

    private static int remaining(GroupOrder o) {
        return o.getTargetQuantity() == null || o.getCurrentQuantity() == null ? 0
                : Math.max(0, o.getTargetQuantity() - o.getCurrentQuantity());
    }

    private static boolean validPrice(SupplierProduct p, GroupOrder o) {
        return p.getPrice() != null && p.getPrice().signum() >= 0 && o.getDiscountRate() != null
                && o.getDiscountRate().signum() >= 0 && o.getDiscountRate().compareTo(new BigDecimal("100")) <= 0;
    }

    static PriceComparison compare(SupplierProduct product, GroupOrder order, int quantity) {
        if (!"open".equals(order.getStatus()) || quantity < 1 || quantity > remaining(order))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Choose a quantity within the remaining open order");
        if (!validPrice(product, order))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Listed prices are unavailable");
        BigDecimal listed = product.getPrice().multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal group = listed.multiply(BigDecimal.ONE.subtract(order.getDiscountRate().movePointLeft(2)))
                .setScale(2, RoundingMode.HALF_UP);
        return new PriceComparison("ZAR", quantity, product.getPrice(), listed, order.getDiscountRate(),
                group, listed.subtract(group), false, false, false, Instant.now());
    }
}
