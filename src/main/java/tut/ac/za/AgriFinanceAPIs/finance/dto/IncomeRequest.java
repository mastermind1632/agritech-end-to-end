package tut.ac.za.AgriFinanceAPIs.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class IncomeRequest {
    private String farmerId;
    private String item;
    private BigDecimal amount;
    private LocalDate date;

    public IncomeRequest() {}

    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }
    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
}
