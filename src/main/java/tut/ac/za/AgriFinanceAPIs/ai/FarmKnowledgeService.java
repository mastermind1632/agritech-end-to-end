package tut.ac.za.AgriFinanceAPIs.ai;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FarmKnowledgeService {
    record Snippet(String id, String title, String content) {}

    private static final List<Snippet> GUIDES = List.of(
            new Snippet("guide:seed", "Crop seeds", "Seed means agricultural planting seed in AgriTech. Choose seed for your crop, local climate, water availability and planting season. Check the packet's variety, germination information and expiry date. Compare the same pack size and include delivery costs. Ask which crop and location before recommending a variety. Catalogue listings are not proof of stock availability."),
            new Snippet("guide:fertiliser", "Fertiliser budgeting", "Fertiliser (fertilizer) spending can be compared in the ledger and Marketplace catalogue. Compare equivalent nutrient content and pack sizes, including transport. Use a soil test and local agricultural advice before choosing nutrient rates. Do not guess application rates or promise yields. Group buying can offer savings only when the actual order terms confirm a discount."),
            new Snippet("guide:ledger", "Farm ledger and budgeting", "My Finances records sales/income and expenses, with amounts, dates and expense categories. Profit is sales minus expenses, not the bank balance. Record seed, fertiliser, transport and labour costs. Use only the current farmer's supplied figures; missing records are unknown, not zero. An AI reply cannot create or modify a ledger entry."),
            new Snippet("guide:buying", "Suppliers and group buying", "Marketplace lists product names and listed prices. Group Orders pools orders for supplier products. An open order has a target quantity, current quantity and discount rate. Check product, quantity, delivery and final price before joining. Do not invent stock, delivery dates or discounts, or claim an order has been placed."),
            new Snippet("guide:water", "Water and irrigation", "For crop irrigation, ask for the crop, growth stage, soil, location and available water. Check soil moisture and irrigation leaks. Exact water requirements depend on local conditions; do not guess a schedule from absent field information."),
            new Snippet("guide:rotation", "Crop rotation", "Crop rotation alternates crop families across seasons. It can help manage soil fertility and pest pressure. Ask about the previous crop, next crop, location and water before suggesting a rotation. No guaranteed yield or profit can be inferred from rotation alone."),
            new Snippet("guide:analytics", "Farmer clusters, nearest matches and anomalies", "K-means clustering groups farmers by spending, orders and product behaviour. Nearest matching finds similar farmer profiles; similarity is not a credit score or proof of eligibility. Anomalies flag unusual spending or order signals for review, not confirmed fraud. Ask the farmer to check the actual analytics results; do not invent cluster membership or private farmer records."));

    // PostgreSQL full-text search supplies stemming and stop-word removal, including seed/seeds.
    private static final String SEARCH = """
            WITH search AS (
                SELECT to_tsquery('english', coalesce(string_agg(quote_literal(term), ' | '), '')) AS query
                FROM unnest(tsvector_to_array(to_tsvector('english', ?))) AS terms(term)
            ), documents(id, title, content) AS (%s), indexed AS (
                SELECT *, to_tsvector('english', %s) AS terms FROM documents
            )
            SELECT id, title, content FROM indexed, search
            WHERE terms @@ search.query
            ORDER BY ts_rank_cd(terms, search.query) DESC, id
            LIMIT ?
            """;

    private static final java.util.Properties EXTERNAL = loadSources();
    private final JdbcTemplate jdbc;

    private static java.util.Properties loadSources() {
        java.util.Properties sources = new java.util.Properties();
        try (var input = FarmKnowledgeService.class.getResourceAsStream("/knowledge/farming.properties")) {
            if (input == null) throw new IllegalStateException("Farming knowledge resource is missing");
            sources.load(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
            return sources;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load farming knowledge", e);
        }
    }

    static List<Snippet> guides() {
        List<Snippet> guides = new ArrayList<>(GUIDES);
        for (String id : EXTERNAL.getProperty("ids").split(",")) {
            guides.add(new Snippet("source:" + id, EXTERNAL.getProperty(id + ".title"),
                    EXTERNAL.getProperty(id + ".content") + "\nRegion: " + EXTERNAL.getProperty(id + ".region")
                    + "\nSource checked: " + EXTERNAL.getProperty(id + ".checkedOn")
                    + ". Editorial summary; agronomist review pending."));
        }
        return List.copyOf(guides);
    }

    static ChatResponse.Source citation(Snippet snippet) {
        if (!snippet.id().startsWith("source:"))
            return new ChatResponse.Source(snippet.id(), snippet.title(), null, "AgriTech",
                    "South Africa / AgriTech app", null, "Internal app guidance or live catalogue");
        String id = snippet.id().substring(7);
        return new ChatResponse.Source(snippet.id(), snippet.title(), EXTERNAL.getProperty(id + ".url"),
                EXTERNAL.getProperty(id + ".publisher"), EXTERNAL.getProperty(id + ".region"),
                EXTERNAL.getProperty(id + ".checkedOn"),
                "Editorial source summary; agronomist review pending. Publication date: "
                        + EXTERNAL.getProperty(id + ".publicationDate"));
    }

    public FarmKnowledgeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Snippet> retrieve(String question) {
        List<Object> guideArgs = new ArrayList<>();
        guideArgs.add(question);
        List<Snippet> guides = guides();
        for (Snippet guide : guides) {
            guideArgs.add(guide.id());
            guideArgs.add(guide.title());
            guideArgs.add(guide.content());
        }
        guideArgs.add(3);
        String values = "VALUES " + String.join(",", java.util.Collections.nCopies(guides.size(), "(?::text, ?::text, ?::text)"));
        List<Snippet> snippets = new ArrayList<>(query(SEARCH.formatted(values, "title || ' ' || content"), guideArgs.toArray()));
        // Retrieve only public catalogue/order facts, never farmer accounts or other farmers' ledgers.
        String catalogue = """
                SELECT 'product:' || p.id::text, p.product_name::text,
                       'Product: ' || p.product_name || '; supplier: ' || s.name ||
                       '; listed price: R' || p.price::text ||
                       coalesce((SELECT '; open group orders: ' || string_agg(
                           'order ' || g.id::text || ', target ' || g.target_quantity::text ||
                           ', current ' || g.current_quantity::text || ', discount ' ||
                           coalesce(g.discount_rate, 0)::text || ' percent', '; ' ORDER BY g.id)
                           FROM group_orders g WHERE g.product_id = p.id AND g.status = 'open'), '')
                FROM supplier_products p JOIN suppliers s ON s.id = p.supplier_id
                """;
        snippets.addAll(query(SEARCH.formatted(catalogue, "title"), question, 3));
        return List.copyOf(snippets);
    }

    private List<Snippet> query(String sql, Object... args) {
        return jdbc.query(sql, (row, index) -> new Snippet(row.getString("id"),
                row.getString("title"), row.getString("content")), args);
    }
}
