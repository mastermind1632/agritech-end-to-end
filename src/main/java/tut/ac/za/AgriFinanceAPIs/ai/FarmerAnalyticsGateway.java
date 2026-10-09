package tut.ac.za.AgriFinanceAPIs.ai;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FarmerAnalyticsGateway {
    private final JdbcTemplate jdbcTemplate;

    public FarmerAnalyticsGateway(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean farmerExists(String farmerId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM farmers WHERE id = ?", Integer.class, farmerId);
        return count != null && count > 0;
    }

    public List<FarmerProfile> farmerProfiles() {
        return jdbcTemplate.query("""
                SELECT f.id,
                       f.name,
                       COALESCE(f.location, '') AS location,
                       COALESCE(e.total_expense, 0) AS total_expense,
                       COALESCE(e.expense_count, 0) AS expense_count,
                       COALESCE(o.order_count, 0) AS order_count,
                       COALESCE(o.order_spend, 0) AS order_spend,
                       COALESCE(e.distinct_items, 0) AS distinct_items,
                       CONCAT_WS(' ', e.expense_terms, o.product_terms) AS terms
                FROM farmers f
                LEFT JOIN (
                    SELECT farmer_id,
                           SUM(amount) AS total_expense,
                           COUNT(*) AS expense_count,
                           COUNT(DISTINCT LOWER(item)) AS distinct_items,
                           STRING_AGG(CONCAT_WS(' ', item, category), ' ') AS expense_terms
                    FROM expenses
                    GROUP BY farmer_id
                ) e ON e.farmer_id = f.id
                LEFT JOIN (
                    SELECT goi.farmer_id,
                           COUNT(*) AS order_count,
                           SUM(goi.total_price) AS order_spend,
                           STRING_AGG(sp.product_name, ' ') AS product_terms
                    FROM group_order_items goi
                    JOIN group_orders go ON go.id = goi.group_order_id
                    JOIN supplier_products sp ON sp.id = go.product_id
                    GROUP BY goi.farmer_id
                ) o ON o.farmer_id = f.id
                ORDER BY f.created_at, f.id
                """, (rs, rowNum) -> new FarmerProfile(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("location"),
                rs.getBigDecimal("total_expense"),
                rs.getLong("expense_count"),
                rs.getLong("order_count"),
                rs.getBigDecimal("order_spend"),
                rs.getLong("distinct_items"),
                tokenize(rs.getString("terms"))));
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> token.length() > 2)
                .collect(Collectors.toSet());
    }
}
