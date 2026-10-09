package tut.ac.za.AgriFinanceAPIs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.finance.FinanceService;
import tut.ac.za.AgriFinanceAPIs.finance.dto.FinanceSummary;
import java.math.BigDecimal;
import java.util.List;

class AssistantAccessTest {
    private Authentication auth() {
        var auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("own-farm");
        return auth;
    }

    @Test
    void rejectsOtherFarmersAnalyticsBeforeQuerying() {
        var service = mock(FarmerAnalyticsService.class);
        var controller = new FarmerAnalyticsController(service);
        assertThatThrownBy(() -> controller.nearestFarmers("other-farm", 5, auth()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.anomalies("other-farm", auth()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(service);
    }

    @Test
    void usesVerifiedOwnLedgerRatherThanClientSuppliedFigures() {
        var service = mock(AssistantService.class);
        var finance = mock(FinanceService.class);
        when(finance.getSummary("own-farm")).thenReturn(new FinanceSummary("own-farm", BigDecimal.TEN, BigDecimal.ONE));
        when(service.chat(eq("seed"), anyString())).thenReturn(new ChatResponse("qwen", "Planting seed", List.of()));
        var response = new AssistantController(service, finance).chat(new ChatRequest("seed", "Invented private figures"), auth());
        assertThat(response.response()).isEqualTo("Planting seed");
        verify(finance).getSummary("own-farm");
        verify(service).chat(eq("seed"), argThat(context -> context.contains("10") && !context.contains("Invented")));
    }
}
