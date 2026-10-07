package tut.ac.za.AgriFinanceAPIs.ai;

import tut.ac.za.AgriFinanceAPIs.ai.AnalyticsDtos.Anomaly;
import tut.ac.za.AgriFinanceAPIs.ai.AnalyticsDtos.AnomalyReport;
import tut.ac.za.AgriFinanceAPIs.ai.AnalyticsDtos.ClusterResponse;
import tut.ac.za.AgriFinanceAPIs.ai.AnalyticsDtos.ClusteredFarmer;
import tut.ac.za.AgriFinanceAPIs.ai.AnalyticsDtos.FarmerCluster;
import tut.ac.za.AgriFinanceAPIs.ai.AnalyticsDtos.FarmerMatch;
import tut.ac.za.AgriFinanceAPIs.ai.AnalyticsDtos.FarmerMatchResponse;
import tut.ac.za.AgriFinanceAPIs.ai.AnalyticsDtos.FarmerSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FarmerAnalyticsService {
    private static final int FEATURE_COUNT = 5;
    private final FarmerAnalyticsGateway gateway;

    public FarmerAnalyticsService(FarmerAnalyticsGateway gateway) {
        this.gateway = gateway;
    }

    public ClusterResponse clusters(int requestedK) {
        List<FarmerProfile> farmers = gateway.farmerProfiles();
        if (farmers.isEmpty()) {
            return new ClusterResponse(requestedK, 0, List.of());
        }

        int k = Math.max(1, Math.min(requestedK, farmers.size()));
        FeatureSpace featureSpace = FeatureSpace.from(farmers);
        List<double[]> centroids = initialCentroids(featureSpace.normalized, k);
        int[] assignments = new int[farmers.size()];

        for (int iteration = 0; iteration < 25; iteration++) {
            boolean changed = assignToNearestCentroid(featureSpace.normalized, centroids, assignments);
            centroids = recomputeCentroids(featureSpace.normalized, assignments, centroids, k);
            if (!changed && iteration > 0) {
                break;
            }
        }

        List<FarmerCluster> clusters = new ArrayList<>();
        for (int clusterId = 0; clusterId < k; clusterId++) {
            List<ClusteredFarmer> members = new ArrayList<>();
            for (int i = 0; i < farmers.size(); i++) {
                if (assignments[i] == clusterId) {
                    members.add(new ClusteredFarmer(snapshot(farmers.get(i)), round(distance(featureSpace.normalized.get(i), centroids.get(clusterId)))));
                }
            }
            members.sort(Comparator.comparing(ClusteredFarmer::distanceToCentroid));
            clusters.add(new FarmerCluster(clusterId, centroidSnapshot(clusterId, centroids.get(clusterId), featureSpace), members));
        }
        return new ClusterResponse(requestedK, k, clusters);
    }

    public FarmerMatchResponse nearestFarmers(String farmerId, int limit) {
        ensureFarmerExists(farmerId);
        List<FarmerProfile> farmers = gateway.farmerProfiles();
        FarmerProfile target = farmers.stream()
                .filter(farmer -> farmer.id().equals(farmerId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found"));

        FeatureSpace featureSpace = FeatureSpace.from(farmers);
        int targetIndex = farmers.indexOf(target);
        List<FarmerMatch> matches = new ArrayList<>();
        for (int i = 0; i < farmers.size(); i++) {
            FarmerProfile candidate = farmers.get(i);
            if (candidate.id().equals(farmerId)) {
                continue;
            }
            double vectorSimilarity = 1.0 / (1.0 + distance(featureSpace.normalized.get(targetIndex), featureSpace.normalized.get(i)));
            double tokenSimilarity = jaccard(target.tokens(), candidate.tokens());
            double locationBoost = sameLocation(target, candidate) ? 0.08 : 0;
            double similarity = Math.min(1.0, (vectorSimilarity * 0.7) + (tokenSimilarity * 0.22) + locationBoost);
            matches.add(new FarmerMatch(snapshot(candidate), round(similarity), matchReasons(target, candidate, tokenSimilarity)));
        }

        return new FarmerMatchResponse(snapshot(target), matches.stream()
                .sorted(Comparator.comparing(FarmerMatch::similarity).reversed())
                .limit(Math.max(1, limit))
                .toList());
    }

    public AnomalyReport anomalies(String farmerId) {
        ensureFarmerExists(farmerId);
        List<FarmerProfile> farmers = gateway.farmerProfiles();
        FarmerProfile target = farmers.stream()
                .filter(farmer -> farmer.id().equals(farmerId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found"));

        List<Anomaly> anomalies = new ArrayList<>();
        checkAnomaly(anomalies, "expense_spike", "totalExpense", target.totalExpense(), farmers.stream()
                .map(FarmerProfile::totalExpense)
                .toList(), "Total expenses are much higher than the farmer population baseline.");
        checkAnomaly(anomalies, "order_spend_spike", "orderSpend", target.orderSpend(), farmers.stream()
                .map(FarmerProfile::orderSpend)
                .toList(), "Group-order spend is unusually high compared with other farmers.");
        checkCountAnomaly(anomalies, "order_activity_spike", "orderCount", target.orderCount(), farmers.stream()
                .map(FarmerProfile::orderCount)
                .toList(), "Group-order activity is unusually high compared with other farmers.");
        checkCountAnomaly(anomalies, "low_data_signal", "expenseCount", target.expenseCount(), farmers.stream()
                .map(FarmerProfile::expenseCount)
                .toList(), "This farmer has very little expense history, so recommendations may be less reliable.");

        return new AnomalyReport(snapshot(target), anomalies);
    }

    private void checkAnomaly(List<Anomaly> anomalies, String type, String metric, BigDecimal value, List<BigDecimal> population, String message) {
        Stats stats = Stats.from(population.stream().mapToDouble(BigDecimal::doubleValue).toArray());
        double zScore = stats.zScore(value.doubleValue());
        if (zScore >= 2.0) {
            anomalies.add(new Anomaly(type, zScore >= 3.0 ? "high" : "medium", metric, money(value), money(stats.mean), message));
        }
    }

    private void checkCountAnomaly(List<Anomaly> anomalies, String type, String metric, long value, List<Long> population, String message) {
        Stats stats = Stats.from(population.stream().mapToDouble(Long::doubleValue).toArray());
        double zScore = "low_data_signal".equals(type) ? stats.lowZScore(value) : stats.zScore(value);
        if (zScore >= 2.0) {
            anomalies.add(new Anomaly(type, zScore >= 3.0 ? "high" : "medium", metric, BigDecimal.valueOf(value), money(stats.mean), message));
        }
    }

    private static boolean assignToNearestCentroid(List<double[]> features, List<double[]> centroids, int[] assignments) {
        boolean changed = false;
        for (int i = 0; i < features.size(); i++) {
            int nearest = 0;
            double nearestDistance = Double.MAX_VALUE;
            for (int j = 0; j < centroids.size(); j++) {
                double currentDistance = distance(features.get(i), centroids.get(j));
                if (currentDistance < nearestDistance) {
                    nearest = j;
                    nearestDistance = currentDistance;
                }
            }
            if (assignments[i] != nearest) {
                assignments[i] = nearest;
                changed = true;
            }
        }
        return changed;
    }

    private static List<double[]> recomputeCentroids(List<double[]> features, int[] assignments, List<double[]> previous, int k) {
        List<double[]> centroids = new ArrayList<>();
        int[] counts = new int[k];
        for (int i = 0; i < k; i++) {
            centroids.add(new double[FEATURE_COUNT]);
        }
        for (int i = 0; i < features.size(); i++) {
            counts[assignments[i]]++;
            for (int j = 0; j < FEATURE_COUNT; j++) {
                centroids.get(assignments[i])[j] += features.get(i)[j];
            }
        }
        for (int i = 0; i < k; i++) {
            if (counts[i] == 0) {
                centroids.set(i, previous.get(i));
                continue;
            }
            for (int j = 0; j < FEATURE_COUNT; j++) {
                centroids.get(i)[j] /= counts[i];
            }
        }
        return centroids;
    }

    private static List<double[]> initialCentroids(List<double[]> features, int k) {
        List<double[]> centroids = new ArrayList<>();
        if (k == 1) {
            centroids.add(features.get(0).clone());
            return centroids;
        }
        for (int i = 0; i < k; i++) {
            int index = (int) Math.round(i * (features.size() - 1) / (double) (k - 1));
            centroids.add(features.get(index).clone());
        }
        return centroids;
    }

    private List<String> matchReasons(FarmerProfile target, FarmerProfile candidate, double tokenSimilarity) {
        List<String> reasons = new ArrayList<>();
        if (sameLocation(target, candidate)) {
            reasons.add("same location");
        }
        if (tokenSimilarity > 0) {
            Set<String> shared = new HashSet<>(target.tokens());
            shared.retainAll(candidate.tokens());
            reasons.add("shared products or expense terms: " + String.join(", ", shared.stream().limit(3).toList()));
        }
        if (Math.abs(target.orderCount() - candidate.orderCount()) <= 1) {
            reasons.add("similar group-order activity");
        }
        if (reasons.isEmpty()) {
            reasons.add("similar spending and activity pattern");
        }
        return reasons;
    }

    private static boolean sameLocation(FarmerProfile first, FarmerProfile second) {
        return first.location() != null
                && !first.location().isBlank()
                && first.location().equalsIgnoreCase(second.location());
    }

    private FarmerSnapshot centroidSnapshot(int clusterId, double[] centroid, FeatureSpace featureSpace) {
        return new FarmerSnapshot(
                "cluster-" + clusterId,
                "Cluster " + clusterId + " centroid",
                "",
                money(featureSpace.denormalize(0, centroid[0])),
                Math.round(featureSpace.denormalize(1, centroid[1])),
                Math.round(featureSpace.denormalize(2, centroid[2])),
                money(featureSpace.denormalize(3, centroid[3])),
                Math.round(featureSpace.denormalize(4, centroid[4])));
    }

    private FarmerSnapshot snapshot(FarmerProfile farmer) {
        return new FarmerSnapshot(
                farmer.id(),
                farmer.name(),
                farmer.location(),
                money(farmer.totalExpense()),
                farmer.expenseCount(),
                farmer.orderCount(),
                money(farmer.orderSpend()),
                farmer.distinctItems());
    }

    private void ensureFarmerExists(String farmerId) {
        if (!gateway.farmerExists(farmerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found");
        }
    }

    private static double distance(double[] first, double[] second) {
        double total = 0;
        for (int i = 0; i < first.length; i++) {
            double diff = first[i] - second[i];
            total += diff * diff;
        }
        return Math.sqrt(total);
    }

    private static double jaccard(Set<String> first, Set<String> second) {
        if (first.isEmpty() && second.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(first);
        intersection.retainAll(second);
        Set<String> union = new HashSet<>(first);
        union.addAll(second);
        return intersection.size() / (double) union.size();
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private record FeatureSpace(List<double[]> normalized, double[] min, double[] max) {
        static FeatureSpace from(List<FarmerProfile> farmers) {
            List<double[]> raw = farmers.stream()
                    .map(farmer -> new double[] {
                            farmer.totalExpense().doubleValue(),
                            farmer.expenseCount(),
                            farmer.orderCount(),
                            farmer.orderSpend().doubleValue(),
                            farmer.distinctItems()
                    })
                    .toList();
            double[] min = new double[FEATURE_COUNT];
            double[] max = new double[FEATURE_COUNT];
            for (int i = 0; i < FEATURE_COUNT; i++) {
                final int index = i;
                min[i] = raw.stream().mapToDouble(values -> values[index]).min().orElse(0);
                max[i] = raw.stream().mapToDouble(values -> values[index]).max().orElse(0);
            }
            List<double[]> normalized = raw.stream()
                    .map(values -> normalize(values, min, max))
                    .toList();
            return new FeatureSpace(normalized, min, max);
        }

        double denormalize(int index, double value) {
            if (max[index] == min[index]) {
                return min[index];
            }
            return min[index] + (value * (max[index] - min[index]));
        }

        private static double[] normalize(double[] values, double[] min, double[] max) {
            double[] normalized = new double[FEATURE_COUNT];
            for (int i = 0; i < FEATURE_COUNT; i++) {
                normalized[i] = max[i] == min[i] ? 0 : (values[i] - min[i]) / (max[i] - min[i]);
            }
            return normalized;
        }
    }

    private record Stats(double mean, double standardDeviation) {
        static Stats from(double[] values) {
            if (values.length == 0) {
                return new Stats(0, 0);
            }
            double mean = 0;
            for (double value : values) {
                mean += value;
            }
            mean /= values.length;
            double variance = 0;
            for (double value : values) {
                double diff = value - mean;
                variance += diff * diff;
            }
            return new Stats(mean, Math.sqrt(variance / values.length));
        }

        double zScore(double value) {
            if (standardDeviation == 0) {
                return 0;
            }
            return (value - mean) / standardDeviation;
        }

        double lowZScore(double value) {
            if (standardDeviation == 0) {
                return 0;
            }
            return (mean - value) / standardDeviation;
        }
    }
}
