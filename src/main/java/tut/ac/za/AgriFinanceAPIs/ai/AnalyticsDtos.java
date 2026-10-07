package tut.ac.za.AgriFinanceAPIs.ai;

import java.math.BigDecimal;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {
    }

    public record FarmerSnapshot(
            String farmerId,
            String name,
            String location,
            BigDecimal totalExpense,
            long expenseCount,
            long orderCount,
            BigDecimal orderSpend,
            long distinctItems) {
    }

    public record ClusterResponse(int requestedClusters, int actualClusters, List<FarmerCluster> clusters) {
    }

    public record FarmerCluster(int clusterId, FarmerSnapshot centroid, List<ClusteredFarmer> farmers) {
    }

    public record ClusteredFarmer(FarmerSnapshot farmer, double distanceToCentroid) {
    }

    public record FarmerMatchResponse(FarmerSnapshot farmer, List<FarmerMatch> matches) {
    }

    public record FarmerMatch(FarmerSnapshot farmer, double similarity, List<String> reasons) {
    }

    public record AnomalyReport(FarmerSnapshot farmer, List<Anomaly> anomalies) {
    }

    public record Anomaly(String type, String severity, String metric, BigDecimal value, BigDecimal baseline, String message) {
    }
}
