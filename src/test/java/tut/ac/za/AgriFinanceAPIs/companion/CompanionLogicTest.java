package tut.ac.za.AgriFinanceAPIs.companion;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompanionLogicTest {

    private final CompanionRules rules = new CompanionRules(new BigDecimal("1000000"));

    private CompanionRules.Profile informal() {
        return new CompanionRules.Profile("NONE", 0, "50K_TO_1M", false, false, "RAINFED", false, false,
                false, false, false, null, 2, null, null);
    }

    @Test
    void informalFarmGetsStructureAndVoluntaryVatGuidanceOnly() {
        CompanionRules.Profile p = informal();
        assertTrue(rules.applies("NO_STRUCTURE", p));
        assertTrue(rules.applies("VAT_VOLUNTARY", p));
        assertFalse(rules.applies("VAT_COMPULSORY", p));
        assertFalse(rules.applies("HAS_EMPLOYEES", p));
        assertFalse(rules.applies("WATER_USE", p));
        assertTrue(rules.applies("NO_BUSINESS_ACCOUNT", p));
        assertTrue(rules.applies("ALWAYS", p));
        assertTrue(rules.applies("NO_STRUCTURE|COMPANY", p), "'|' means any of the rules");
        assertFalse(rules.applies("COMPANY|HAS_EMPLOYEES", p));
    }

    @Test
    void employerWithBoreholeIrrigationGetsLabourAndWaterSteps() {
        CompanionRules.Profile p = new CompanionRules.Profile("PTY_LTD", 6, "1M_TO_5M", false, true, "BOREHOLE", true, true,
                true, true, true, LocalDate.of(2023, 4, 12), 2, "2023/123456/07", "9123456789");
        assertTrue(rules.applies("HAS_EMPLOYEES", p));
        assertTrue(rules.applies("WATER_USE", p));
        assertTrue(rules.applies("VAT_COMPULSORY", p));
        assertTrue(rules.applies("COMPANY", p));
        assertTrue(rules.applies("LIVESTOCK", p));
        assertFalse(rules.applies("NO_STRUCTURE", p));

        List<CompanionRules.DeadlineSpec> d = rules.deadlines(p, LocalDate.of(2026, 10, 14));
        assertTrue(d.stream().anyMatch(x -> x.code().equals("CIPC_ANNUAL_RETURN")));
        assertTrue(d.stream().anyMatch(x -> x.code().startsWith("EMP201_")));
        assertTrue(d.stream().anyMatch(x -> x.code().startsWith("SARS_PROVISIONAL_")));
        assertTrue(d.stream().allMatch(x -> !x.dueDate().isBefore(LocalDate.of(2026, 10, 14))));
        CompanionRules.DeadlineSpec cipc = d.stream().filter(x -> x.code().equals("CIPC_ANNUAL_RETURN")).findFirst().orElseThrow();
        assertEquals(2027, cipc.dueDate().getYear());
    }

    @Test
    void vatThresholdComesFromConfiguration() {
        CompanionRules higher = new CompanionRules(new BigDecimal("2300000"));
        CompanionRules.Profile p = new CompanionRules.Profile("PTY_LTD", 0, "1M_TO_5M", false, false, null, false, false,
                false, false, true, null, 2, null, null);
        assertFalse(higher.applies("VAT_COMPULSORY", p));
        assertTrue(higher.applies("VAT_VOLUNTARY", p));
    }

    @Test
    void progressIsAPercentage() {
        assertEquals(50, CompanionRules.progress(3, 6));
        assertEquals(0, CompanionRules.progress(0, 0));
    }

    @Test
    void cashflowSpreadsCostsOverTheSeasonAndBooksIncomeAtHarvest() {
        CashflowProjector.CropInput tomatoes = new CashflowProjector.CropInput("Tomatoes", new BigDecimal("2"),
                new BigDecimal("40000"), "kg", new BigDecimal("6"), new BigDecimal("60000"), 11, 2);
        CashflowProjector.Projection p = CashflowProjector.project(List.of(tomatoes), new BigDecimal("1000"),
                new BigDecimal("50000"), YearMonth.of(2026, 11));
        assertEquals(12, p.months().size());
        CashflowProjector.CropBudget b = p.crops().get(0);
        assertEquals(new BigDecimal("480000.00"), b.revenue());
        assertEquals(new BigDecimal("120000.00"), b.cost());
        assertEquals(new BigDecimal("1.50"), b.breakEvenPrice());
        // Nov, Dec, Jan, Feb carry R30 000 of crop cost each; February also receives the harvest income.
        assertEquals(new BigDecimal("30000.00"), p.months().get(0).cropCosts());
        assertEquals(new BigDecimal("0.00"), p.months().get(4).cropCosts());
        assertEquals(new BigDecimal("480000.00"), p.months().get(3).income());
        assertEquals("2027-01", p.lowestMonth());
        assertEquals(new BigDecimal("-43000.00"), p.lowestCumulative());
    }
}
