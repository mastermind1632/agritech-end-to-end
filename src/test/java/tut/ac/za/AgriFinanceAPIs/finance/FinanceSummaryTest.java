package tut.ac.za.AgriFinanceAPIs.finance;

import org.junit.jupiter.api.Test;
import tut.ac.za.AgriFinanceAPIs.finance.dto.FinanceSummary;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceSummaryTest {

    @Test
    void profitIsIncomeMinusExpenses() {
        FinanceSummary s = new FinanceSummary("f1", new BigDecimal("1500.50"), new BigDecimal("400.25"));
        assertThat(s.getProfit()).isEqualByComparingTo("1100.25");
    }

    @Test
    void profitCanBeNegative() {
        FinanceSummary s = new FinanceSummary("f1", BigDecimal.ZERO, new BigDecimal("10"));
        assertThat(s.getProfit()).isEqualByComparingTo("-10");
    }
}
