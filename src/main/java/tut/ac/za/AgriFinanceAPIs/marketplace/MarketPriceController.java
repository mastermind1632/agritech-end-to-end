package tut.ac.za.AgriFinanceAPIs.marketplace;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Crop price board for the dashboard widget: average asking prices on the Farm Market and average prices actually
 * paid in completed sales over the last 90 days. (Municipal fresh-produce market prices can be added as a feed.)
 */
@RestController
public class MarketPriceController {

    public record PriceRow(String product, String unit, BigDecimal averageAsking, BigDecimal lowest, BigDecimal highest,
                           long listings, BigDecimal averageSold, long sales) { }

    private final ListingRepository listings;
    private final MarketOrderRepository orders;

    public MarketPriceController(ListingRepository listings, MarketOrderRepository orders) {
        this.listings = listings;
        this.orders = orders;
    }

    @GetMapping("/api/market/prices")
    public List<PriceRow> prices(@RequestParam(defaultValue = "12") int limit) {
        Map<String, Object[]> sold = new HashMap<>();
        for (Object[] r : orders.soldBoard(LocalDateTime.now().minusDays(90))) sold.put(r[0] + "|" + r[1], r);
        List<PriceRow> out = new ArrayList<>();
        for (Object[] r : listings.priceBoard()) {
            Object[] s = sold.get(r[0] + "|" + r[1]);
            out.add(new PriceRow(title(String.valueOf(r[0])), String.valueOf(r[1]), money(r[2]), (BigDecimal) r[3], (BigDecimal) r[4],
                    ((Number) r[5]).longValue(), s == null ? null : money(s[2]), s == null ? 0 : ((Number) s[3]).longValue()));
            if (out.size() >= Math.max(1, Math.min(limit, 50))) break;
        }
        return out;
    }

    private static BigDecimal money(Object avg) {
        return avg == null ? null : BigDecimal.valueOf(((Number) avg).doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }

    private static String title(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
