package tut.ac.za.AgriFinanceAPIs.finance;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import tut.ac.za.AgriFinanceAPIs.finance.dto.ExpenseRequest;
import tut.ac.za.AgriFinanceAPIs.finance.dto.FinanceSummary;
import tut.ac.za.AgriFinanceAPIs.finance.dto.IncomeRequest;

import java.util.List;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public Expense createExpense(@RequestBody ExpenseRequest request, Authentication auth) {
        request.setFarmerId(auth.getName());
        return financeService.createExpense(request);
    }

    @GetMapping("/expenses/{farmerId}")
    public List<Expense> getExpenses(@PathVariable String farmerId, Authentication auth) {
        if (!farmerId.equals(auth.getName())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"You can only access your own finance records");
        return financeService.getExpenses(farmerId);
    }

    @PutMapping("/expenses/{id}")
    public Expense updateExpense(@PathVariable Long id, @RequestBody ExpenseRequest request, Authentication auth) {
        request.setFarmerId(auth.getName());
        return financeService.updateExpense(id, request);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@PathVariable Long id, @RequestParam String farmerId, Authentication auth) {
        if (!farmerId.equals(auth.getName())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"You can only delete your own finance records");
        financeService.deleteExpense(id, farmerId);
    }

    @PostMapping("/income")
    @ResponseStatus(HttpStatus.CREATED)
    public Income createIncome(@RequestBody IncomeRequest request, Authentication auth) {
        request.setFarmerId(auth.getName());
        return financeService.createIncome(request);
    }

    @GetMapping("/income/{farmerId}")
    public List<Income> getIncome(@PathVariable String farmerId, Authentication auth) {
        if (!farmerId.equals(auth.getName())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"You can only access your own finance records");
        return financeService.getIncome(farmerId);
    }

    @PutMapping("/income/{id}")
    public Income updateIncome(@PathVariable Long id, @RequestBody IncomeRequest request, Authentication auth) {
        request.setFarmerId(auth.getName());
        return financeService.updateIncome(id, request);
    }

    @DeleteMapping("/income/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIncome(@PathVariable Long id, @RequestParam String farmerId, Authentication auth) {
        if (!farmerId.equals(auth.getName())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"You can only delete your own finance records");
        financeService.deleteIncome(id, farmerId);
    }

    @GetMapping("/{farmerId}/summary")
    public FinanceSummary getSummary(@PathVariable String farmerId, Authentication auth) {
        if (!farmerId.equals(auth.getName())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,"You can only access your own finance records");
        return financeService.getSummary(farmerId);
    }
}
