package tut.ac.za.AgriFinanceAPIs.finance.dto;

import java.math.BigDecimal;

public class FinanceSummary {
    private final String farmerId;
    private final BigDecimal totalIncome;
    private final BigDecimal totalExpenses;
    private final BigDecimal profit;

    public FinanceSummary(String farmerId, BigDecimal totalIncome, BigDecimal totalExpenses) {
        this.farmerId = farmerId;
        this.totalIncome = totalIncome;
        this.totalExpenses = totalExpenses;
        this.profit = totalIncome.subtract(totalExpenses);
    }

    public String getFarmerId() { return farmerId; }
    public BigDecimal getTotalIncome() { return totalIncome; }
    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public BigDecimal getProfit() { return profit; }
}
