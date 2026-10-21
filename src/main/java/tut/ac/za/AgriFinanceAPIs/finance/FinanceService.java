package tut.ac.za.AgriFinanceAPIs.finance;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;
import tut.ac.za.AgriFinanceAPIs.finance.dto.ExpenseRequest;
import tut.ac.za.AgriFinanceAPIs.finance.dto.FinanceSummary;
import tut.ac.za.AgriFinanceAPIs.finance.dto.IncomeRequest;

import java.math.BigDecimal;
import java.util.List;

@Service
public class FinanceService {

    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final FarmerRepository farmerRepository;

    public FinanceService(ExpenseRepository expenseRepository,
                          IncomeRepository incomeRepository,
                          FarmerRepository farmerRepository) {
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.farmerRepository = farmerRepository;
    }

    public Expense createExpense(ExpenseRequest request) {
        validateFarmer(request.getFarmerId());
        validateAmount(request.getAmount());
        requireText(request.getItem(), "Item is required");

        Expense expense = new Expense();
        expense.setFarmerId(request.getFarmerId());
        expense.setItem(request.getItem());
        expense.setCategory(request.getCategory());
        expense.setAmount(request.getAmount());
        expense.setDate(request.getDate() != null ? request.getDate() : java.time.LocalDate.now());
        expense.setCrop(cropOf(request.getCrop()));
        return expenseRepository.save(expense);
    }

    public List<Expense> getExpenses(String farmerId) {
        validateFarmer(farmerId);
        return expenseRepository.findAllByFarmerIdOrderByDateDesc(farmerId);
    }

    public Expense updateExpense(Long id, ExpenseRequest request) {
        validateFarmer(request.getFarmerId());
        validateAmount(request.getAmount());
        requireText(request.getItem(), "Item is required");

        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));

        if (!expense.getFarmerId().equals(request.getFarmerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Expense does not belong to this farmer");
        }

        expense.setItem(request.getItem());
        expense.setCategory(request.getCategory());
        expense.setAmount(request.getAmount());
        expense.setDate(request.getDate() != null ? request.getDate() : java.time.LocalDate.now());
        expense.setCrop(cropOf(request.getCrop()));
        return expenseRepository.save(expense);
    }

    public void deleteExpense(Long id, String farmerId) {
        validateFarmer(farmerId);
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
        if (!expense.getFarmerId().equals(farmerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Expense does not belong to this farmer");
        }
        expenseRepository.delete(expense);
    }

    public Income createIncome(IncomeRequest request) {
        validateFarmer(request.getFarmerId());
        validateAmount(request.getAmount());
        requireText(request.getItem(), "Item is required");

        Income income = new Income();
        income.setFarmerId(request.getFarmerId());
        income.setItem(request.getItem());
        income.setAmount(request.getAmount());
        income.setDate(request.getDate() != null ? request.getDate() : java.time.LocalDate.now());
        return incomeRepository.save(income);
    }

    public List<Income> getIncome(String farmerId) {
        validateFarmer(farmerId);
        return incomeRepository.findAllByFarmerIdOrderByDateDesc(farmerId);
    }

    public Income updateIncome(Long id, IncomeRequest request) {
        validateFarmer(request.getFarmerId());
        validateAmount(request.getAmount());
        requireText(request.getItem(), "Item is required");

        Income income = incomeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Income record not found"));

        if (!income.getFarmerId().equals(request.getFarmerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Income record does not belong to this farmer");
        }

        income.setItem(request.getItem());
        income.setAmount(request.getAmount());
        income.setDate(request.getDate() != null ? request.getDate() : java.time.LocalDate.now());
        return incomeRepository.save(income);
    }

    public void deleteIncome(Long id, String farmerId) {
        validateFarmer(farmerId);
        Income income = incomeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Income record not found"));
        if (!income.getFarmerId().equals(farmerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Income record does not belong to this farmer");
        }
        incomeRepository.delete(income);
    }

    public FinanceSummary getSummary(String farmerId) {
        validateFarmer(farmerId);
        BigDecimal totalIncome = incomeRepository.sumAmountByFarmerId(farmerId);
        BigDecimal totalExpenses = expenseRepository.sumAmountByFarmerId(farmerId);
        if (totalIncome == null) totalIncome = BigDecimal.ZERO;
        if (totalExpenses == null) totalExpenses = BigDecimal.ZERO;
        return new FinanceSummary(farmerId, totalIncome, totalExpenses);
    }

    public record MonthTotals(String month, BigDecimal income, BigDecimal expenses, BigDecimal net,
                              java.util.Map<String, BigDecimal> expensesByCategory) { }

    /** Income and expenses per month for the last N months (oldest first), for charts and lender reports. */
    public List<MonthTotals> monthly(String farmerId, int months) {
        validateFarmer(farmerId);
        int n = Math.max(1, Math.min(months, 36));
        java.time.YearMonth end = java.time.YearMonth.now();
        java.time.YearMonth start = end.minusMonths(n - 1L);
        java.time.LocalDate from = start.atDay(1);
        java.util.Map<java.time.YearMonth, BigDecimal[]> totals = new java.util.TreeMap<>();
        java.util.Map<java.time.YearMonth, java.util.Map<String, BigDecimal>> cats = new java.util.TreeMap<>();
        for (java.time.YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            totals.put(m, new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
            cats.put(m, new java.util.TreeMap<>());
        }
        for (Income i : incomeRepository.findAllByFarmerIdOrderByDateDesc(farmerId)) {
            if (i.getDate() == null || i.getDate().isBefore(from)) continue;
            BigDecimal[] t = totals.get(java.time.YearMonth.from(i.getDate()));
            if (t != null) t[0] = t[0].add(i.getAmount());
        }
        for (Expense e : expenseRepository.findAllByFarmerIdOrderByDateDesc(farmerId)) {
            if (e.getDate() == null || e.getDate().isBefore(from)) continue;
            java.time.YearMonth ym = java.time.YearMonth.from(e.getDate());
            BigDecimal[] t = totals.get(ym);
            if (t == null) continue;
            t[1] = t[1].add(e.getAmount());
            String c = e.getCategory() == null || e.getCategory().isBlank() ? "Other" : e.getCategory();
            cats.get(ym).merge(c, e.getAmount(), BigDecimal::add);
        }
        List<MonthTotals> out = new java.util.ArrayList<>();
        totals.forEach((m, t) -> out.add(new MonthTotals(m.toString(), t[0], t[1], t[0].subtract(t[1]), cats.get(m))));
        return out;
    }

    /** Excel workbook with a monthly summary and every income and expense line (for tax time or a loan application). */
    public byte[] exportWorkbook(String farmerId) {
        validateFarmer(farmerId);
        List<List<Object>> summary = new java.util.ArrayList<>();
        summary.add(List.of("Month", "Income (R)", "Expenses (R)", "Net (R)"));
        for (MonthTotals m : monthly(farmerId, 24)) summary.add(List.of(m.month(), m.income(), m.expenses(), m.net()));
        List<List<Object>> lines = new java.util.ArrayList<>();
        lines.add(List.of("Date", "Type", "Item", "Category", "Crop", "Amount (R)", "Source"));
        for (Income i : incomeRepository.findAllByFarmerIdOrderByDateDesc(farmerId)) {
            lines.add(java.util.Arrays.asList(i.getDate(), "Income", i.getItem(), "", "", i.getAmount(), i.getSource() == null ? "Manual" : i.getSource()));
        }
        for (Expense e : expenseRepository.findAllByFarmerIdOrderByDateDesc(farmerId)) {
            lines.add(java.util.Arrays.asList(e.getDate(), "Expense", e.getItem(), e.getCategory(), e.getCrop(), e.getAmount(), e.getSource() == null ? "Manual" : e.getSource()));
        }
        return new tut.ac.za.AgriFinanceAPIs.reports.SimpleXlsxWriter().sheet("Monthly summary", summary).sheet("All records", lines).toBytes();
    }

    private static String cropOf(String crop) {
        if (crop == null || crop.isBlank()) return null;
        String c = crop.trim();
        return c.length() > 60 ? c.substring(0, 60) : c;
    }

    private void validateFarmer(String farmerId) {
        if (farmerId == null || farmerId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "farmerId is required");
        }
        if (!farmerRepository.existsById(farmerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Farmer not found");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be zero or greater");
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
