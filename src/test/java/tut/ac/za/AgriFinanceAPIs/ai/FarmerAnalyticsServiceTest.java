package tut.ac.za.AgriFinanceAPIs.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FarmerAnalyticsServiceTest {
    @Mock FarmerAnalyticsGateway gateway;

    private FarmerAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new FarmerAnalyticsService(gateway);
    }

    @Test
    void clustersFarmersWithKMeans() {
        when(gateway.farmerProfiles()).thenReturn(sampleFarmers());

        var response = service.clusters(2);

        assertThat(response.actualClusters()).isEqualTo(2);
        assertThat(response.clusters()).hasSize(2);
        assertThat(response.clusters())
                .flatExtracting(cluster -> cluster.farmers())
                .hasSize(8);
    }

    @Test
    void ranksNearestFarmersBySimilaritySignals() {
        when(gateway.farmerExists("farmer-1")).thenReturn(true);
        when(gateway.farmerProfiles()).thenReturn(sampleFarmers());

        var response = service.nearestFarmers("farmer-1", 2);

        assertThat(response.matches()).hasSize(2);
        assertThat(response.matches().get(0).farmer().farmerId()).isEqualTo("farmer-2");
        assertThat(response.matches().get(0).reasons()).contains("same location");
    }

    @Test
    void reportsPopulationAnomalies() {
        when(gateway.farmerExists("farmer-4")).thenReturn(true);
        when(gateway.farmerProfiles()).thenReturn(sampleFarmers());

        var response = service.anomalies("farmer-4");

        assertThat(response.anomalies())
                .extracting(anomaly -> anomaly.type())
                .contains("expense_spike", "order_spend_spike");
    }

    private List<FarmerProfile> sampleFarmers() {
        return List.of(
                new FarmerProfile("farmer-1", "Amina", "Limpopo", bd("100.00"), 2, 1, bd("50.00"), 2, Set.of("seed", "maize")),
                new FarmerProfile("farmer-2", "Bongani", "Limpopo", bd("110.00"), 2, 1, bd("55.00"), 2, Set.of("seed", "maize")),
                new FarmerProfile("farmer-3", "Chris", "Gauteng", bd("130.00"), 3, 1, bd("60.00"), 3, Set.of("fertilizer", "spinach")),
                new FarmerProfile("farmer-5", "Elias", "Gauteng", bd("90.00"), 2, 1, bd("45.00"), 2, Set.of("fertilizer", "spinach")),
                new FarmerProfile("farmer-6", "Fikile", "North West", bd("120.00"), 3, 1, bd("65.00"), 3, Set.of("seed", "beans")),
                new FarmerProfile("farmer-7", "Grace", "North West", bd("95.00"), 2, 1, bd("40.00"), 2, Set.of("seed", "beans")),
                new FarmerProfile("farmer-8", "Hassan", "Limpopo", bd("115.00"), 2, 1, bd("58.00"), 2, Set.of("seed", "maize")),
                new FarmerProfile("farmer-4", "Dineo", "Free State", bd("2000.00"), 12, 10, bd("1800.00"), 8, Set.of("tractor", "diesel")));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
