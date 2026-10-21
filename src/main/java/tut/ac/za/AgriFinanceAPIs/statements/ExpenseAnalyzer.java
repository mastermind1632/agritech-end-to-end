package tut.ac.za.AgriFinanceAPIs.statements;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analysis step of the expense pipeline: monthly summaries, cost per crop and per hectare, duplicate detection
 * and unusual-expense flags. Pure Java so it can be unit tested without a database.
 */
public final class ExpenseAnalyzer {

    /** amount is signed (negative = money out). crop may be null. */
    public record Txn(long id, LocalDate date, String description, BigDecimal amount, ExpenseCategory category, String crop) { }

    /** How a category is spread over crops when a transaction has no crop tag. */
    public record AllocationRule(ExpenseCategory category, String method, String crop) { }

    public record MonthRow(String month, BigDecimal income, BigDecimal expenses, BigDecimal net,
                           Map<String, BigDecimal> byCategory) { }

    public record CategoryTotal(String category, String label, BigDecimal amount, double share, int count) { }

    public record CropCost(String crop, BigDecimal direct, BigDecimal allocated, BigDecimal total,
                           BigDecimal hectares, BigDecimal perHectare) { }

    public record Flag(long id, String type, String reason) { }

    public record Insight(String level, String text) { }

    public record Report(List<MonthRow> months, List<CategoryTotal> categories, List<CropCost> crops,
                         BigDecimal unallocated, List<Flag> flags, List<Insight> insights,
                         BigDecimal totalIncome, BigDecimal totalExpenses, BigDecimal personalSpend) { }

    private ExpenseAnalyzer() { }

    public static Report analyse(List<Txn> txns, Map<String, BigDecimal> hectaresByCrop, List<AllocationRule> rules) {
        Map<String, BigDecimal> hectares = hectaresByCrop == null ? Map.of() : hectaresByCrop;
        List<Txn> sorted = new ArrayList<>(txns);
        sorted.sort(Comparator.comparing(Txn::date).thenComparing(Txn::id));

        // ---- monthly summaries
        Map<YearMonth, BigDecimal[]> monthTotals = new TreeMap<>();
        Map<YearMonth, Map<String, BigDecimal>> monthCats = new TreeMap<>();
        Map<ExpenseCategory, BigDecimal> catTotals = new EnumMap<>(ExpenseCategory.class);
        Map<ExpenseCategory, Integer> catCounts = new EnumMap<>(ExpenseCategory.class);
        BigDecimal totalIncome = BigDecimal.ZERO, totalExpenses = BigDecimal.ZERO, personal = BigDecimal.ZERO;

        for (Txn t : sorted) {
            if (t.category() == ExpenseCategory.TRANSFER) continue;
            YearMonth ym = YearMonth.from(t.date());
            BigDecimal[] mt = monthTotals.computeIfAbsent(ym, k -> new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
            if (t.category() == ExpenseCategory.PERSONAL) {
                personal = personal.add(t.amount().abs());
                continue;
            }
            if (t.amount().signum() > 0) {
                mt[0] = mt[0].add(t.amount());
                totalIncome = totalIncome.add(t.amount());
            } else {
                BigDecimal a = t.amount().abs();
                mt[1] = mt[1].add(a);
                totalExpenses = totalExpenses.add(a);
                catTotals.merge(t.category(), a, BigDecimal::add);
                catCounts.merge(t.category(), 1, Integer::sum);
                monthCats.computeIfAbsent(ym, k -> new TreeMap<>()).merge(t.category().name(), a, BigDecimal::add);
            }
        }
        List<MonthRow> months = new ArrayList<>();
        for (Map.Entry<YearMonth, BigDecimal[]> e : monthTotals.entrySet()) {
            BigDecimal in = e.getValue()[0], out = e.getValue()[1];
            months.add(new MonthRow(e.getKey().toString(), scale(in), scale(out), scale(in.subtract(out)),
                    monthCats.getOrDefault(e.getKey(), Map.of())));
        }

        final BigDecimal expTotal = totalExpenses;
        List<CategoryTotal> categories = catTotals.entrySet().stream()
                .sorted(Map.Entry.<ExpenseCategory, BigDecimal>comparingByValue().reversed())
                .map(e -> new CategoryTotal(e.getKey().name(), e.getKey().label(), scale(e.getValue()),
                        expTotal.signum() == 0 ? 0 : e.getValue().divide(expTotal, 4, RoundingMode.HALF_UP).doubleValue(),
                        catCounts.getOrDefault(e.getKey(), 0)))
                .toList();

        // ---- cost per crop
        Map<String, BigDecimal> direct = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, BigDecimal> allocated = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        BigDecimal unallocated = BigDecimal.ZERO;
        Map<ExpenseCategory, AllocationRule> ruleByCat = new EnumMap<>(ExpenseCategory.class);
        if (rules != null) for (AllocationRule r : rules) if (r.category() != null) ruleByCat.put(r.category(), r);
        BigDecimal totalHa = hectares.values().stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);

        for (Txn t : sorted) {
            if (t.amount().signum() >= 0 || t.category() == null || !t.category().isFarmExpense()) continue;
            BigDecimal a = t.amount().abs();
            if (t.crop() != null && !t.crop().isBlank()) {
                direct.merge(t.crop().trim(), a, BigDecimal::add);
                continue;
            }
            AllocationRule rule = ruleByCat.get(t.category());
            if (rule != null && "FIXED_CROP".equals(rule.method()) && rule.crop() != null) {
                allocated.merge(rule.crop(), a, BigDecimal::add);
            } else if (rule != null && "BY_HECTARES".equals(rule.method()) && totalHa.signum() > 0) {
                for (Map.Entry<String, BigDecimal> h : hectares.entrySet()) {
                    if (h.getValue() == null || h.getValue().signum() <= 0) continue;
                    allocated.merge(h.getKey(), a.multiply(h.getValue()).divide(totalHa, 2, RoundingMode.HALF_UP), BigDecimal::add);
                }
            } else {
                unallocated = unallocated.add(a);
            }
        }
        Set<String> cropNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        cropNames.addAll(direct.keySet());
        cropNames.addAll(allocated.keySet());
        cropNames.addAll(hectares.keySet());
        List<CropCost> crops = new ArrayList<>();
        for (String c : cropNames) {
            BigDecimal d = direct.getOrDefault(c, BigDecimal.ZERO);
            BigDecimal al = allocated.getOrDefault(c, BigDecimal.ZERO);
            BigDecimal total = d.add(al);
            BigDecimal ha = findIgnoreCase(hectares, c);
            BigDecimal perHa = ha == null || ha.signum() <= 0 ? null : total.divide(ha, 2, RoundingMode.HALF_UP);
            crops.add(new CropCost(c, scale(d), scale(al), scale(total), ha, perHa));
        }

        // ---- flags: duplicates and unusual amounts
        List<Flag> flags = new ArrayList<>();
        flags.addAll(duplicates(sorted));
        flags.addAll(anomalies(sorted));

        // ---- plain-language insights
        List<Insight> insights = new ArrayList<>();
        if (!categories.isEmpty()) {
            CategoryTotal top = categories.get(0);
            insights.add(new Insight("info", top.label() + " is your largest cost: R" + top.amount().toPlainString()
                    + " (" + Math.round(top.share() * 100) + "% of farm spending)."));
        }
        if (months.size() >= 6) {
            BigDecimal recent = BigDecimal.ZERO, earlier = BigDecimal.ZERO;
            for (int i = months.size() - 3; i < months.size(); i++) recent = recent.add(months.get(i).expenses());
            for (int i = months.size() - 6; i < months.size() - 3; i++) earlier = earlier.add(months.get(i).expenses());
            if (earlier.signum() > 0) {
                double change = recent.subtract(earlier).divide(earlier, 4, RoundingMode.HALF_UP).doubleValue();
                if (change > 0.15) insights.add(new Insight("warning", "Spending over the last 3 months is "
                        + Math.round(change * 100) + "% higher than the 3 months before."));
                else if (change < -0.15) insights.add(new Insight("good", "Spending over the last 3 months is "
                        + Math.round(-change * 100) + "% lower than the 3 months before."));
            }
        }
        BigDecimal debitsAll = totalExpenses.add(personal);
        if (debitsAll.signum() > 0 && personal.divide(debitsAll, 4, RoundingMode.HALF_UP).doubleValue() > 0.25) {
            insights.add(new Insight("warning", "About " + personal.multiply(BigDecimal.valueOf(100)).divide(debitsAll, 0, RoundingMode.HALF_UP)
                    + "% of the money leaving this account is personal spending. A separate business account makes the farm's numbers clearer for lenders."));
        }
        if (unallocated.signum() > 0 && !hectares.isEmpty()) {
            insights.add(new Insight("info", "R" + scale(unallocated).toPlainString()
                    + " of farm costs is not linked to a crop. Tag transactions or add a split rule to see true cost per crop."));
        }
        long dupes = flags.stream().filter(f -> f.type().equals("DUPLICATE")).count();
        if (dupes > 0) insights.add(new Insight("warning", dupes + " possible duplicate transaction(s) found. Check they were not imported twice."));

        return new Report(months, categories, crops, scale(unallocated), flags, insights,
                scale(totalIncome), scale(totalExpenses), scale(personal));
    }

    /** Same amount, same direction, same merchant, within one day: probably the same transaction imported twice. */
    static List<Flag> duplicates(List<Txn> sorted) {
        List<Flag> out = new ArrayList<>();
        Set<Long> flagged = new HashSet<>();
        for (int i = 0; i < sorted.size(); i++) {
            Txn a = sorted.get(i);
            for (int j = i + 1; j < sorted.size(); j++) {
                Txn b = sorted.get(j);
                long days = ChronoUnit.DAYS.between(a.date(), b.date());
                if (days > 1) break;
                if (flagged.contains(b.id())) continue;
                if (a.amount().compareTo(b.amount()) == 0
                        && TransactionCategorizer.merchantKey(a.description()).equals(TransactionCategorizer.merchantKey(b.description()))
                        && !TransactionCategorizer.merchantKey(a.description()).isEmpty()) {
                    out.add(new Flag(b.id(), "DUPLICATE", "Same amount and payee as another transaction on " + a.date()));
                    flagged.add(b.id());
                }
            }
        }
        return out;
    }

    /**
     * Unusual expenses using a robust z-score (median absolute deviation), which is not thrown off by the very
     * outliers it is looking for. Needs at least five transactions in a category to say what "usual" is.
     */
    static List<Flag> anomalies(List<Txn> sorted) {
        Map<ExpenseCategory, List<Txn>> byCat = sorted.stream()
                .filter(t -> t.amount().signum() < 0 && t.category() != null && t.category().isFarmExpense())
                .collect(Collectors.groupingBy(Txn::category, () -> new EnumMap<>(ExpenseCategory.class), Collectors.toList()));
        List<Flag> out = new ArrayList<>();
        for (Map.Entry<ExpenseCategory, List<Txn>> e : byCat.entrySet()) {
            List<Txn> list = e.getValue();
            if (list.size() < 5) continue;
            double[] values = list.stream().mapToDouble(t -> t.amount().abs().doubleValue()).sorted().toArray();
            double median = median(values);
            double[] dev = Arrays.stream(values).map(v -> Math.abs(v - median)).sorted().toArray();
            double mad = median(dev);
            for (Txn t : list) {
                double v = t.amount().abs().doubleValue();
                boolean unusual;
                if (mad > 0) unusual = 0.6745 * (v - median) / mad > 3.5;
                else unusual = median > 0 && v > 3 * median;
                if (unusual && v > 200) {
                    out.add(new Flag(t.id(), "UNUSUAL", String.format(Locale.ROOT,
                            "R%,.2f is much higher than your usual R%,.2f for %s", v, median, e.getKey().label())));
                }
            }
        }
        return out;
    }

    private static double median(double[] sorted) {
        if (sorted.length == 0) return 0;
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
    }

    private static BigDecimal findIgnoreCase(Map<String, BigDecimal> m, String key) {
        for (Map.Entry<String, BigDecimal> e : m.entrySet()) if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        return null;
    }

    private static BigDecimal scale(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
}
