package tut.ac.za.AgriFinanceAPIs.statements;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.statements.StatementDtos.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statements")
public class StatementController {

    private final StatementService service;

    public StatementController(StatementService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public Map<String, Object> categories() {
        Map<String, String> expense = new LinkedHashMap<>();
        Map<String, String> income = new LinkedHashMap<>();
        for (ExpenseCategory c : ExpenseCategory.expenses()) expense.put(c.name(), c.label());
        for (ExpenseCategory c : ExpenseCategory.incomes()) income.put(c.name(), c.label());
        return Map.of("expense", expense, "income", income);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StatementDetail upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "consent", defaultValue = "false") boolean consent,
                                  @RequestParam(value = "keepOriginal", defaultValue = "false") boolean keepOriginal,
                                  Authentication auth) throws IOException {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No file uploaded");
        return service.upload(auth.getName(), file.getOriginalFilename(), file.getBytes(), consent, keepOriginal);
    }

    @GetMapping
    public List<StatementView> list(Authentication auth) {
        return service.list(auth.getName());
    }

    @GetMapping("/{id}")
    public StatementDetail detail(@PathVariable String id, Authentication auth) {
        return service.detail(id, auth.getName());
    }

    @PutMapping("/transactions/{txnId}")
    public TxnView update(@PathVariable Long txnId, @RequestBody TxnUpdate body, Authentication auth) {
        return service.updateTransaction(auth.getName(), txnId, body);
    }

    @PostMapping("/{id}/confirm")
    public StatementDetail confirm(@PathVariable String id, Authentication auth) {
        return service.confirm(auth.getName(), id);
    }

    @PostMapping("/{id}/reopen")
    public StatementDetail reopen(@PathVariable String id, Authentication auth) {
        return service.reopen(auth.getName(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @RequestParam(defaultValue = "false") boolean keepLedger, Authentication auth) {
        service.delete(auth.getName(), id, keepLedger);
    }

    @GetMapping("/analysis")
    public Analysis analysis(Authentication auth) {
        return service.analysis(auth.getName());
    }

    @GetMapping("/rules")
    public List<RuleView> rules(Authentication auth) {
        return service.rules(auth.getName());
    }

    @PostMapping("/rules")
    public List<RuleView> saveRule(@RequestBody RuleRequest body, Authentication auth) {
        return service.saveRule(auth.getName(), body);
    }

    @DeleteMapping("/rules/{id}")
    public List<RuleView> deleteRule(@PathVariable Long id, Authentication auth) {
        return service.deleteRule(auth.getName(), id);
    }

    @GetMapping("/export.xlsx")
    public ResponseEntity<byte[]> xlsx(Authentication auth) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"farm-expense-analysis.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.workbook(auth.getName()));
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> csv(Authentication auth) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"farm-transactions.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(service.csv(auth.getName()).getBytes(StandardCharsets.UTF_8));
    }
}
