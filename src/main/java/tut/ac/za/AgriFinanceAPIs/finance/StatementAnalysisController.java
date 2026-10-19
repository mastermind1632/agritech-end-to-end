package tut.ac.za.AgriFinanceAPIs.finance;

import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/finance/statement")
public class StatementAnalysisController {

    public record StatementLine(String date, String description, BigDecimal amount, String cropTag) {}
    public record StatementUploadRequest(
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            List<StatementLine> transactions
    ) {}

    @PostMapping("/analyze")
    public Map<String, Object> analyzeStatement(@RequestBody StatementUploadRequest request) {
        BigDecimal totalNet = request.transactions().stream()
                .map(StatementLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal calculatedClosing = request.openingBalance().add(totalNet);
        boolean balanceValid = calculatedClosing.compareTo(request.closingBalance()) == 0;

        Map<String, BigDecimal> categoryTotals = new LinkedHashMap<>();
        Map<String, BigDecimal> cropCostTotals = new LinkedHashMap<>();
        List<Map<String, Object>> categorizedTransactions = new ArrayList<>();
        List<Map<String, Object>> anomalies = new ArrayList<>();

        for (StatementLine line : request.transactions()) {
            String category = categorize(line.description(), line.amount());
            BigDecimal absAmount = line.amount().abs();

            categoryTotals.merge(category, absAmount, BigDecimal::add);
            if (line.cropTag() != null && !line.cropTag().isBlank() && line.amount().compareTo(BigDecimal.ZERO) < 0) {
                cropCostTotals.merge(line.cropTag(), absAmount, BigDecimal::add);
            }

            boolean unusual = absAmount.compareTo(new BigDecimal("25000")) > 0;
            Map<String, Object> item = Map.of(
                    "date", line.date(),
                    "description", line.description(),
                    "amount", line.amount(),
                    "category", category,
                    "cropTag", line.cropTag() == null ? "Unallocated" : line.cropTag(),
                    "unusualFlag", unusual
            );
            categorizedTransactions.add(item);
            if (unusual) {
                anomalies.add(item);
            }
        }

        return Map.of(
                "balanceValid", balanceValid,
                "expectedClosingBalance", calculatedClosing,
                "reportedClosingBalance", request.closingBalance(),
                "status", balanceValid ? "VALIDATED" : "FLAGGED_FOR_REVIEW",
                "categoryTotals", categoryTotals,
                "cropCostTotals", cropCostTotals,
                "anomalies", anomalies,
                "transactions", categorizedTransactions
        );
    }

    private String categorize(String description, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            return "Produce sales & income";
        }
        String desc = description == null ? "" : description.toLowerCase();
        if (desc.contains("engen") || desc.contains("shell") || desc.contains("sasol") || desc.contains("total") || desc.contains("diesel") || desc.contains("fuel")) {
            return "Fuel";
        }
        if (desc.contains("omnia") || desc.contains("kynoch") || desc.contains("fertil") || desc.contains("npk") || desc.contains("urea")) {
            return "Fertiliser";
        }
        if (desc.contains("seed") || desc.contains("stark") || desc.contains("sakata") || desc.contains("syngenta") || desc.contains("seedling")) {
            return "Seeds and seedlings";
        }
        if (desc.contains("spray") || desc.contains("chem") || desc.contains("pest") || desc.contains("herbicide")) {
            return "Agrochemicals";
        }
        if (desc.contains("wage") || desc.contains("payroll") || desc.contains("labour") || desc.contains("uif")) {
            return "Labour and wages";
        }
        if (desc.contains("eskom") || desc.contains("water") || desc.contains("municipality")) {
            return "Electricity and water";
        }
        if (desc.contains("fee") || desc.contains("charge") || desc.contains("bank")) {
            return "Bank fees";
        }
        return "General farm expense";
    }
}
