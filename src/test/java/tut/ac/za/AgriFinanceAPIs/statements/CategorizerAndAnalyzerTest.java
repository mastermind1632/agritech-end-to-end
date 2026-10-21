package tut.ac.za.AgriFinanceAPIs.statements;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CategorizerAndAnalyzerTest {

    private final TransactionCategorizer plain = new TransactionCategorizer(Map.of(), List.of());

    @Test
    void rulesRecogniseSouthAfricanFarmMerchants() {
        assertEquals(ExpenseCategory.FUEL, plain.categorise("POS PURCHASE ENGEN TZANEEN 1234", true).category());
        assertEquals(ExpenseCategory.FERTILISER, plain.categorise("OMNIA FERTILIZER POLOKWANE", true).category());
        assertEquals(ExpenseCategory.SEEDS, plain.categorise("Starke Ayres seedlings", true).category());
        assertEquals(ExpenseCategory.BANK_FEES, plain.categorise("#MONTHLY ACCOUNT FEE", true).category());
        assertEquals(ExpenseCategory.LABOUR, plain.categorise("WAGES MARCH", true).category());
        assertEquals(ExpenseCategory.PERSONAL, plain.categorise("CHECKERS HYPER", true).category());
        assertEquals(ExpenseCategory.PRODUCE_SALES, plain.categorise("PAYMENT FROM FRESHMARK", false).category());
        assertEquals(ExpenseCategory.GRANTS, plain.categorise("CASP GRANT DEPT OF AGRIC", false).category());
    }

    @Test
    void unknownMerchantsNeedReview() {
        TransactionCategorizer.Result r = plain.categorise("XYZ TRADING 991", true);
        assertEquals(ExpenseCategory.OTHER_EXPENSE, r.category());
        assertTrue(r.needsReview());
    }

    @Test
    void farmerCorrectionsWin() {
        String key = TransactionCategorizer.merchantKey("POS PURCHASE MAKHADO AGRI 0042");
        TransactionCategorizer c = new TransactionCategorizer(Map.of(key, ExpenseCategory.FERTILISER), List.of());
        TransactionCategorizer.Result r = c.categorise("POS PURCHASE MAKHADO AGRI 0199", true);
        assertEquals(ExpenseCategory.FERTILISER, r.category());
        assertEquals("LEARNED", r.source());
        assertFalse(r.needsReview());
    }

    @Test
    void naiveBayesLearnsFromConfirmedHistory() {
        List<Map.Entry<String, ExpenseCategory>> history = new ArrayList<>();
        for (int i = 0; i < 10; i++) history.add(Map.entry("MOKGADI TRACTOR HIRE " + i, ExpenseCategory.TRANSPORT));
        for (int i = 0; i < 10; i++) history.add(Map.entry("LEBOWA BOREHOLE PUMP " + i, ExpenseCategory.REPAIRS));
        TransactionCategorizer c = new TransactionCategorizer(Map.of(), history);
        TransactionCategorizer.Result r = c.categorise("LEBOWA BOREHOLE 77", true);
        assertEquals(ExpenseCategory.REPAIRS, r.category());
        assertEquals("ML", r.source());
    }

    @Test
    void merchantKeyIgnoresNumbersAndBankingNoise() {
        assertEquals(TransactionCategorizer.merchantKey("POS PURCHASE ENGEN TZANEEN 1234 05/03"),
                TransactionCategorizer.merchantKey("pos purchase Engen Tzaneen 9999"));
    }

    @Test
    void analyserBuildsMonthlySummariesCostPerCropAndFlags() {
        List<ExpenseAnalyzer.Txn> t = new ArrayList<>();
        long id = 1;
        for (int i = 0; i < 6; i++) {
            t.add(new ExpenseAnalyzer.Txn(id++, LocalDate.of(2026, 1 + i, 3), "ENGEN TZANEEN", new BigDecimal("-500.00"), ExpenseCategory.FUEL, null));
        }
        t.add(new ExpenseAnalyzer.Txn(id++, LocalDate.of(2026, 6, 20), "ENGEN TZANEEN BULK", new BigDecimal("-9000.00"), ExpenseCategory.FUEL, null));
        t.add(new ExpenseAnalyzer.Txn(id++, LocalDate.of(2026, 2, 10), "OMNIA", new BigDecimal("-3000.00"), ExpenseCategory.FERTILISER, null));
        t.add(new ExpenseAnalyzer.Txn(id++, LocalDate.of(2026, 2, 11), "STARKE AYRES", new BigDecimal("-800.00"), ExpenseCategory.SEEDS, "Tomatoes"));
        t.add(new ExpenseAnalyzer.Txn(id++, LocalDate.of(2026, 3, 1), "FRESHMARK", new BigDecimal("7000.00"), ExpenseCategory.PRODUCE_SALES, null));
        t.add(new ExpenseAnalyzer.Txn(id++, LocalDate.of(2026, 3, 2), "CHECKERS", new BigDecimal("-400.00"), ExpenseCategory.PERSONAL, null));
        t.add(new ExpenseAnalyzer.Txn(99, LocalDate.of(2026, 2, 10), "OMNIA", new BigDecimal("-3000.00"), ExpenseCategory.FERTILISER, null));

        Map<String, BigDecimal> ha = new LinkedHashMap<>();
        ha.put("Tomatoes", new BigDecimal("1"));
        ha.put("Butternut", new BigDecimal("3"));
        ExpenseAnalyzer.Report r = ExpenseAnalyzer.analyse(t,
                ha, List.of(new ExpenseAnalyzer.AllocationRule(ExpenseCategory.FERTILISER, "BY_HECTARES", null)));

        assertEquals(6, r.months().size());
        assertEquals(new BigDecimal("7000.00"), r.totalIncome());
        assertEquals(new BigDecimal("400.00"), r.personalSpend());
        assertEquals("FUEL", r.categories().get(0).category());

        ExpenseAnalyzer.CropCost tomatoes = r.crops().stream().filter(c -> c.crop().equals("Tomatoes")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("800.00"), tomatoes.direct());
        assertEquals(new BigDecimal("1500.00"), tomatoes.allocated(), "R6000 fertiliser split 1:3 by hectares");
        assertEquals(new BigDecimal("2300.00"), tomatoes.perHectare());

        assertTrue(r.flags().stream().anyMatch(f -> f.id() == 99 && f.type().equals("DUPLICATE")));
        assertTrue(r.flags().stream().anyMatch(f -> f.type().equals("UNUSUAL") && f.reason().contains("9,000.00")));
        assertFalse(r.insights().isEmpty());
    }
}
