package tut.ac.za.AgriFinanceAPIs.statements;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class StatementDtos {
    private StatementDtos() { }

    public record StatementView(String id, String fileName, String sourceType, String extractionMethod, String bank,
                                String accountHint, LocalDate periodStart, LocalDate periodEnd, BigDecimal openingBalance,
                                BigDecimal closingBalance, BigDecimal computedClosing, String balanceCheck, String status,
                                String message, int transactionCount, long needsReview, long flagged,
                                BigDecimal moneyIn, BigDecimal moneyOut, boolean originalKept, LocalDateTime createdAt,
                                LocalDateTime confirmedAt) { }

    public record TxnView(Long id, LocalDate date, String description, BigDecimal amount, BigDecimal balance,
                          String category, String categoryLabel, double confidence, String source, boolean needsReview,
                          boolean directionCertain, String crop, String flag, String flagReason, Long matchedOrderId,
                          String ledgerType) { }

    public record StatementDetail(StatementView statement, List<TxnView> transactions, List<String> crops,
                                  Map<String, String> categories) { }

    public record TxnUpdate(String category, String crop, Boolean clearFlag, Boolean applyToSimilar) { }

    public record RuleRequest(String category, String method, String crop) { }

    public record RuleView(Long id, String category, String categoryLabel, String method, String crop) { }

    public record Analysis(ExpenseAnalyzer.Report report, Map<String, BigDecimal> hectares, List<RuleView> rules,
                           long statements, long transactions) { }
}
