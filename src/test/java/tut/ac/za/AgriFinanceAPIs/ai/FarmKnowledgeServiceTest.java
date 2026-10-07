package tut.ac.za.AgriFinanceAPIs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class FarmKnowledgeServiceTest {
    @Test
    void publishedSourcesIncludeScopeDatesAndHonestReviewStatus() {
        var sources = FarmKnowledgeService.guides().stream().filter(s -> s.id().startsWith("source:"))
                .map(FarmKnowledgeService::citation).toList();
        assertThat(sources).hasSize(2);
        for (var source : sources) {
            assertThat(source.url()).startsWith("https://www.fao.org/");
            assertThat(source.checkedOn()).matches("\\d{4}-\\d{2}-\\d{2}");
            assertThat(source.region()).contains("local");
            assertThat(source.reviewStatus()).contains("review pending", "Not stated");
        }
    }
    @Test
    void searchesGuidesAndPublicCatalogueWithBoundParameters() {
        List<String> statements = new ArrayList<>();
        List<Object[]> parameters = new ArrayList<>();
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
                statements.add(sql);
                parameters.add(args);
                return List.of();
            }
        };
        String question = "seed'; DROP TABLE farmers; --";
        assertThat(new FarmKnowledgeService(jdbc).retrieve(question)).isEmpty();
        assertThat(statements).hasSize(2);
        assertThat(statements.get(0)).contains("to_tsvector", "VALUES").doesNotContain(question);
        assertThat(statements.get(1)).contains("supplier_products", "group_orders")
                .doesNotContain(question, "FROM farmers", "FROM expenses", "FROM income", "password_hash");
        assertThat(parameters.get(0)[0]).isEqualTo(question);
        assertThat(parameters.get(1)[0]).isEqualTo(question);
        assertThat(parameters.get(0)).contains("guide:seed", "Crop seeds");
    }
}
