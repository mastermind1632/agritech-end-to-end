package tut.ac.za.AgriFinanceAPIs.companion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;

/**
 * Budget and 12-month cash-flow projection. Farm income arrives in lumps at harvest while costs are spread through
 * the season, so the projection is monthly and seasonal: each crop's production cost is spread evenly from planting
 * to harvest and its income lands in the harvest month. Fixed overheads (from the farmer's actual spending) are
 * added every month. Pure Java.
 */
public final class CashflowProjector {

    public record CropInput(String crop, BigDecimal hectares, BigDecimal yieldPerHa, String yieldUnit,
                            BigDecimal pricePerUnit, BigDecimal costPerHa, int plantMonth, int harvestMonth) { }

    public record CropBudget(String crop, BigDecimal hectares, BigDecimal expectedVolume, String unit,
                             BigDecimal revenue, BigDecimal cost, BigDecimal margin, BigDecimal marginPerHa,
                             BigDecimal breakEvenPrice) { }

    public record MonthFlow(String month, BigDecimal income, BigDecimal cropCosts, BigDecimal overheads,
                            BigDecimal net, BigDecimal cumulative) { }

    public record Projection(List<CropBudget> crops, List<MonthFlow> months, BigDecimal totalIncome,
                             BigDecimal totalCosts, BigDecimal lowestCumulative, String lowestMonth) { }

    private CashflowProjector() { }

    public static Projection project(List<CropInput> crops, BigDecimal monthlyOverheads, BigDecimal openingCash,
                                     YearMonth start) {
        BigDecimal overhead = monthlyOverheads == null ? BigDecimal.ZERO : monthlyOverheads;
        BigDecimal[] income = new BigDecimal[12];
        BigDecimal[] costs = new BigDecimal[12];
        Arrays.fill(income, BigDecimal.ZERO);
        Arrays.fill(costs, BigDecimal.ZERO);
        List<CropBudget> budgets = new ArrayList<>();

        for (CropInput c : crops) {
            BigDecimal ha = nz(c.hectares());
            BigDecimal volume = ha.multiply(nz(c.yieldPerHa()));
            BigDecimal revenue = volume.multiply(nz(c.pricePerUnit())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cost = ha.multiply(nz(c.costPerHa())).setScale(2, RoundingMode.HALF_UP);
            BigDecimal margin = revenue.subtract(cost);
            BigDecimal marginPerHa = ha.signum() > 0 ? margin.divide(ha, 2, RoundingMode.HALF_UP) : null;
            BigDecimal breakEven = volume.signum() > 0 ? cost.divide(volume, 2, RoundingMode.HALF_UP) : null;
            budgets.add(new CropBudget(c.crop(), ha, volume.setScale(2, RoundingMode.HALF_UP), c.yieldUnit(),
                    revenue, cost, margin, marginPerHa, breakEven));

            int plant = clampMonth(c.plantMonth());
            int harvest = clampMonth(c.harvestMonth());
            int seasonLength = ((harvest - plant + 12) % 12) + 1;
            BigDecimal perMonth = cost.divide(BigDecimal.valueOf(seasonLength), 2, RoundingMode.HALF_UP);
            for (int k = 0; k < seasonLength; k++) {
                int month = ((plant - 1 + k) % 12) + 1;
                costs[indexOf(start, month)] = costs[indexOf(start, month)].add(perMonth);
            }
            income[indexOf(start, harvest)] = income[indexOf(start, harvest)].add(revenue);
        }

        List<MonthFlow> months = new ArrayList<>();
        BigDecimal cumulative = nz(openingCash);
        BigDecimal lowest = null;
        String lowestMonth = null;
        BigDecimal totalIncome = BigDecimal.ZERO, totalCosts = BigDecimal.ZERO;
        for (int i = 0; i < 12; i++) {
            BigDecimal out = costs[i].add(overhead);
            BigDecimal net = income[i].subtract(out);
            cumulative = cumulative.add(net);
            totalIncome = totalIncome.add(income[i]);
            totalCosts = totalCosts.add(out);
            String label = start.plusMonths(i).toString();
            months.add(new MonthFlow(label, s(income[i]), s(costs[i]), s(overhead), s(net), s(cumulative)));
            if (lowest == null || cumulative.compareTo(lowest) < 0) { lowest = cumulative; lowestMonth = label; }
        }
        return new Projection(budgets, months, s(totalIncome), s(totalCosts), s(lowest), lowestMonth);
    }

    /** Position (0-11) of a calendar month in the 12-month window that begins at start. */
    static int indexOf(YearMonth start, int month) {
        return ((month - start.getMonthValue()) + 12) % 12;
    }

    private static int clampMonth(int m) { return m < 1 || m > 12 ? 1 : m; }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static BigDecimal s(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
}
