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
