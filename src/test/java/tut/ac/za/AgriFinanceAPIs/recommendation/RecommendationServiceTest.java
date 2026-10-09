package tut.ac.za.AgriFinanceAPIs.recommendation;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;
import tut.ac.za.AgriFinanceAPIs.finance.*;
import tut.ac.za.AgriFinanceAPIs.supplier.*;
import tut.ac.za.AgriFinanceAPIs.grouporder.*;

class RecommendationServiceTest {
    private SupplierProduct product() {
        SupplierProduct p = new SupplierProduct(); p.setId("maize");
        p.setProductName("Maize seed"); p.setPrice(new BigDecimal("99.99")); return p;
    }
    private GroupOrder order() {
        GroupOrder o = new GroupOrder(); o.setId("order"); o.setProductId("maize");
        o.setTargetQuantity(10); o.setCurrentQuantity(2); o.setDiscountRate(new BigDecimal("10")); return o;
    }
    @Test void matchesCropRatherThanGenericSeedWords() {
        assertThat(RecommendationService.matches("Maize planting seed", "Maize seed 5 kg")).isTrue();
        assertThat(RecommendationService.matches("Bean seeds", "Maize seed")).isFalse();
        assertThat(RecommendationService.matches("Seed", "Maize seed")).isFalse();
    }
    @Test void calculatesSameProductPricesWithoutInventingConfirmedSavings() {
        var quote = RecommendationService.compare(product(), order(), 3);
        assertThat(quote.listedTotal()).isEqualByComparingTo("299.97");
        assertThat(quote.indicativeGroupTotal()).isEqualByComparingTo("269.97");
        assertThat(quote.indicativeDifference()).isEqualByComparingTo("30.00");
        assertThat(quote.supplierDiscountConfirmed()).isFalse();
        assertThat(quote.deliveryKnown()).isFalse();
        assertThat(quote.finalTotalKnown()).isFalse();
    }
    @Test void rejectsInvalidQuantityClosedOrdersAndInvalidPrices() {
        var p = product(); var o = order();
        assertThatThrownBy(() -> RecommendationService.compare(p,o,0)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> RecommendationService.compare(p,o,9)).isInstanceOf(RuntimeException.class);
        o.setStatus("closed");
        assertThatThrownBy(() -> RecommendationService.compare(p,o,1)).isInstanceOf(RuntimeException.class);
        o.setStatus("open"); o.setDiscountRate(new BigDecimal("101"));
        assertThatThrownBy(() -> RecommendationService.compare(p,o,1)).isInstanceOf(RuntimeException.class);
    }
    @Test void recommendsOnlyOwnPurchaseMatchesAndDoesNotPersistDuplicates() {
        var farmers=mock(FarmerRepository.class); var expenses=mock(ExpenseRepository.class);
        var products=mock(SupplierProductRepository.class); var orders=mock(GroupOrderRepository.class);
        var items=mock(GroupOrderItemRepository.class); var recs=mock(AiRecommendationRepository.class);
        when(farmers.existsById("own")).thenReturn(true);
        Expense e=new Expense(); e.setItem("Maize seed");
        when(expenses.findAllByFarmerIdOrderByDateDesc("own")).thenReturn(List.of(e));
        when(orders.findAllByStatusOrderByCreatedAtDesc("open")).thenReturn(List.of(order()));
        when(products.findById("maize")).thenReturn(java.util.Optional.of(product()));
        var service=new RecommendationService(recs,farmers,expenses,products,orders,items);
        assertThat(service.generate("own")).hasSize(1);
        assertThat(service.generate("own")).hasSize(1);
        verifyNoInteractions(recs);
        var chosen=product();chosen.setComparisonKey("maize-A");chosen.setPackSize(new BigDecimal("5"));chosen.setPackUnit("kg");
        var alternative=product();alternative.setId("alternative");alternative.setComparisonKey("maize-A");
        alternative.setPackSize(new BigDecimal("10"));alternative.setPackUnit("kg");alternative.setPrice(new BigDecimal("150"));
        var unrelated=product();unrelated.setId("unrelated");unrelated.setComparisonKey("maize-B");
        unrelated.setPackSize(BigDecimal.ONE);unrelated.setPackUnit("kg");
        when(products.findById("maize")).thenReturn(java.util.Optional.of(chosen));
        when(products.findAll()).thenReturn(List.of(chosen,alternative,unrelated));
        var comparison=service.generate("own").getFirst().equivalentPrices();
        assertThat(comparison).hasSize(2);
        assertThat(comparison.getFirst().productId()).isEqualTo("alternative");
        assertThat(comparison.getFirst().pricePerUnit()).isEqualByComparingTo("15");
        when(expenses.findAllByFarmerIdOrderByDateDesc("own")).thenReturn(List.of());
        assertThat(service.generate("own")).isEmpty();
        when(expenses.findAllByFarmerIdOrderByDateDesc("own")).thenReturn(List.of(e));
        when(items.existsByGroupOrderIdAndFarmerId("order","own")).thenReturn(true);
        assertThat(service.generate("own")).isEmpty();
    }
    @Test void controllerIgnoresSubmittedFarmerIdAndRejectsCrossAccountRead() {
        var service=mock(RecommendationService.class);
        var controller=new RecommendationController(service);
        var authentication=mock(org.springframework.security.core.Authentication.class);
        when(authentication.getName()).thenReturn("own");
        var request=new tut.ac.za.AgriFinanceAPIs.recommendation.dto.RecommendationRequest();
        request.setFarmerId("someone-else");
        controller.generate(request,authentication);
        verify(service).generate("own");
        assertThatThrownBy(() -> controller.forFarmer("someone-else",authentication))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        controller.quote("order",2,authentication);
        verify(service).quote("own","order",2);
    }
}
