package tut.ac.za.AgriFinanceAPIs.statements;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;
import tut.ac.za.AgriFinanceAPIs.companion.CompanionService;
import tut.ac.za.AgriFinanceAPIs.finance.Expense;
import tut.ac.za.AgriFinanceAPIs.finance.ExpenseRepository;
import tut.ac.za.AgriFinanceAPIs.finance.Income;
import tut.ac.za.AgriFinanceAPIs.finance.IncomeRepository;
import tut.ac.za.AgriFinanceAPIs.marketplace.MarketOrder;
import tut.ac.za.AgriFinanceAPIs.marketplace.MarketOrderRepository;
import tut.ac.za.AgriFinanceAPIs.notification.NotificationHub;
import tut.ac.za.AgriFinanceAPIs.privacy.PrivacyService;
import tut.ac.za.AgriFinanceAPIs.reports.SimpleXlsxWriter;
import tut.ac.za.AgriFinanceAPIs.statements.StatementDtos.*;
import tut.ac.za.AgriFinanceAPIs.vault.VaultService;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module 5: farm expense analysis. The pipeline from the design guide:
 * upload -> detect type -> extract (CSV here, PDF text / OCR in the document-service) -> parse -> validate the
 * balance -> categorise (rules, the farmer's corrections, a personal classifier, optional AI) -> farmer review ->
 * import into the ledger -> analyse (monthly, cost per crop, duplicates, unusual expenses) -> reports.
 *
 * POPIA: processing needs the farmer's consent; the original file is discarded after extraction unless the farmer
 * chooses to keep it (then it is stored encrypted in the vault).
 */
@Service
public class StatementService {

    static final long MAX_BYTES = 10L * 1024 * 1024;

    private final BankStatementRepository statements;
    private final StatementTransactionRepository txns;
    private final CategoryMemoryRepository memory;
    private final AllocationRuleRepository rules;
    private final ExpenseRepository expenses;
    private final IncomeRepository incomes;
    private final MarketOrderRepository orders;
    private final DocumentServiceClient documentService;
    private final AiCategorizer ai;
    private final PrivacyService privacy;
    private final VaultService vault;
    private final CompanionService companion;
    private final NotificationHub notifications;
    private final AuditService audit;

    public StatementService(BankStatementRepository statements, StatementTransactionRepository txns, CategoryMemoryRepository memory,
                            AllocationRuleRepository rules, ExpenseRepository expenses, IncomeRepository incomes,
                            MarketOrderRepository orders, DocumentServiceClient documentService, AiCategorizer ai,
                            PrivacyService privacy, VaultService vault, CompanionService companion,
                            NotificationHub notifications, AuditService audit) {
        this.statements = statements;
        this.txns = txns;
        this.memory = memory;
        this.rules = rules;
        this.expenses = expenses;
        this.incomes = incomes;
        this.orders = orders;
        this.documentService = documentService;
        this.ai = ai;
        this.privacy = privacy;
        this.vault = vault;
        this.companion = companion;
        this.notifications = notifications;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ upload and processing

    @Transactional
    public StatementDetail upload(String farmerId, String fileName, byte[] bytes, boolean consent, boolean keepOriginal) {
        if (bytes == null || bytes.length == 0) throw bad("The file is empty");
        if (bytes.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.valueOf(413), "Statements can be at most 10 MB");
        if (!privacy.hasConsent(farmerId, "STATEMENT_PROCESSING")) {
            if (!consent) throw bad("Please agree that we may read this statement to categorise your spending (POPIA consent)");
            privacy.record(farmerId, "STATEMENT_PROCESSING", true);
        }
        String type = detectType(bytes, fileName);
        if (type == null) throw bad("Upload the bank's CSV export, its PDF statement, or a clear photo (JPEG or PNG)");

        BankStatement s = new BankStatement();
        s.setFarmerId(farmerId);
        s.setFileName(clip(fileName == null ? "statement" : fileName, 200));
        s.setSourceType(type);
        s.setStatus("PROCESSING");
        statements.save(s);

        StatementParser.ParsedStatement parsed;
        if ("CSV".equals(type)) {
            s.setExtractionMethod("csv");
            parsed = StatementParser.parseCsv(decode(bytes));
        } else {
            DocumentServiceClient.Extraction ex = documentService.extract(fileName, bytes);
            if (ex == null) {
                return fail(s, "Reading PDFs and photos needs the document service, which is not running. "
                        + "Download the CSV export from your bank's app or internet banking and upload that instead - it is also more accurate.");
            }
            s.setExtractionMethod(ex.method());
            if (ex.lines() == null || ex.lines().isEmpty()) {
                return fail(s, "No text could be read from this file. "
                        + (ex.warnings() == null ? "" : String.join(" ", ex.warnings())) + " Try the bank's CSV export instead.");
            }
            parsed = StatementParser.parseText(ex.lines());
            if (ex.warnings() != null && !ex.warnings().isEmpty()) {
                List<String> w = new ArrayList<>(parsed.warnings());
                w.addAll(ex.warnings());
                parsed = new StatementParser.ParsedStatement(parsed.bank(), parsed.accountHint(), parsed.openingBalance(),
                        parsed.closingBalance(), parsed.periodStart(), parsed.periodEnd(), parsed.transactions(), w);
            }
        }
        if (parsed.transactions().isEmpty()) {
            return fail(s, "No transactions were recognised. " + String.join(" ", parsed.warnings()));
        }

        s.setBank(parsed.bank());
        s.setAccountHint(parsed.accountHint());
        s.setPeriodStart(parsed.periodStart());
        s.setPeriodEnd(parsed.periodEnd());
        s.setOpeningBalance(parsed.openingBalance());
        s.setClosingBalance(parsed.closingBalance());
        s.setComputedClosing(parsed.computedClosing());
        s.setBalanceCheck(parsed.balanceCheck());
        s.setTransactionCount(parsed.transactions().size());

        // Categorise with the farmer's own memory and history first.
        TransactionCategorizer categorizer = categorizerFor(farmerId);
        List<StatementTransaction> created = new ArrayList<>();
        for (StatementParser.ParsedTxn p : parsed.transactions()) {
            StatementTransaction t = new StatementTransaction();
            t.setStatementId(s.getId());
            t.setFarmerId(farmerId);
            t.setTxnDate(p.date());
            t.setDescription(clip(p.description().isBlank() ? "(no description)" : p.description(), 300));
            t.setAmount(p.amount());
            t.setBalance(p.balance());
            t.setDirectionCertain(p.directionCertain());
            TransactionCategorizer.Result r = categorizer.categorise(p.description(), p.isDebit());
            t.setCategory(r.category().name());
            t.setConfidence(r.confidence());
            t.setCategorySource(r.source());
            t.setNeedsReview(r.needsReview() || !p.directionCertain());
            created.add(t);
        }
        matchMarketplaceSales(farmerId, created);
        applyAi(created);
        flag(farmerId, created);
        txns.saveAll(created);

        if (keepOriginal) {
            var doc = vault.store(farmerId, "Bank statement " + (parsed.periodStart() == null ? "" : parsed.periodStart() + " to " + parsed.periodEnd()),
                    "BANK_STATEMENT", fileName, bytes, null, "STATEMENT", s.getId());
            s.setOriginalDocumentId(doc.id());
        }
        long review = created.stream().filter(StatementTransaction::isNeedsReview).count();
        String msg = parsed.transactions().size() + " transactions read" + (review > 0 ? ", " + review + " need your review" : "") + ". ";
        if ("FAILED".equals(parsed.balanceCheck())) {
            msg += "The balances do not add up (opening + transactions = R" + parsed.computedClosing() + ", statement says R"
                    + parsed.closingBalance() + "), so some lines may have been missed. Check the list against your statement.";
        } else if ("PASSED".equals(parsed.balanceCheck())) {
            msg += "Balance check passed: every transaction was read.";
        }
        if (!parsed.warnings().isEmpty() && !"PASSED".equals(parsed.balanceCheck())) {
            msg += " " + parsed.warnings().get(0);
        }
        s.setMessage(msg);
        s.setStatus("REVIEW");
        statements.save(s);
        audit.record(farmerId, "STATEMENT_UPLOADED", "STATEMENT", s.getId(), type + ", " + created.size() + " txns, balance " + parsed.balanceCheck());
        notifications.notify(farmerId, "STATEMENT", "Statement ready for review", msg, "statements");
        return detail(s.getId(), farmerId);
    }

    private StatementDetail fail(BankStatement s, String message) {
        s.setStatus("FAILED");
        s.setMessage(message);
        statements.save(s);
        return detail(s.getId(), s.getFarmerId());
    }

    /** Credits that match a completed Farm Market sale are already income in the ledger; mark them so they are not counted twice. */
    private void matchMarketplaceSales(String farmerId, List<StatementTransaction> list) {
        List<MarketOrder> sales = orders.findBySellerIdAndStatusAndCompletedAtAfter(farmerId, "COMPLETED", LocalDateTime.now().minusMonths(18));
        Set<Long> used = new HashSet<>();
        for (StatementTransaction t : list) {
            if (t.getAmount().signum() <= 0) continue;
            for (MarketOrder o : sales) {
                if (used.contains(o.getId()) || o.getCompletedAt() == null) continue;
                long days = Math.abs(ChronoUnit.DAYS.between(o.getCompletedAt().toLocalDate(), t.getTxnDate()));
                if (days <= 7 && o.getTotalAmount().compareTo(t.getAmount()) == 0) {
                    t.setMatchedOrderId(o.getId());
                    t.setCategory(ExpenseCategory.PRODUCE_SALES.name());
                    t.setConfidence(0.99);
                    t.setCategorySource("MARKET_MATCH");
                    t.setNeedsReview(false);
                    used.add(o.getId());
                    break;
                }
            }
        }
    }

    private void applyAi(List<StatementTransaction> list) {
        if (!ai.isEnabled()) return;
        List<StatementTransaction> unsure = list.stream().filter(t -> "DEFAULT".equals(t.getCategorySource())).limit(40).toList();
        if (unsure.isEmpty()) return;
        Map<Integer, ExpenseCategory> s = ai.suggest(unsure.stream().map(StatementTransaction::getDescription).toList(),
                unsure.stream().map(t -> t.getAmount().signum() < 0).toList());
        s.forEach((i, c) -> {
            StatementTransaction t = unsure.get(i);
            t.setCategory(c.name());
            t.setConfidence(0.6);
            t.setCategorySource("AI");
            t.setNeedsReview(true);
        });
    }

    /** Duplicates (also against earlier statements) and unusual amounts, using the farmer's full history. */
    private void flag(String farmerId, List<StatementTransaction> fresh) {
        List<ExpenseAnalyzer.Txn> all = new ArrayList<>();
        long tempId = -1;
        Map<Long, StatementTransaction> byTemp = new HashMap<>();
        for (StatementTransaction t : txns.confirmed(farmerId)) all.add(toAnalyzer(t, t.getId()));
        for (StatementTransaction t : fresh) {
            byTemp.put(tempId, t);
            all.add(toAnalyzer(t, tempId));
            tempId--;
        }
        all.sort(Comparator.comparing(ExpenseAnalyzer.Txn::date));
        for (ExpenseAnalyzer.Flag f : ExpenseAnalyzer.duplicates(all)) {
            StatementTransaction t = byTemp.get(f.id());
            if (t != null) { t.setFlag("DUPLICATE"); t.setFlagReason(f.reason()); t.setNeedsReview(true); }
        }
        for (ExpenseAnalyzer.Flag f : ExpenseAnalyzer.anomalies(all)) {
            StatementTransaction t = byTemp.get(f.id());
            if (t != null && t.getFlag() == null) { t.setFlag("UNUSUAL"); t.setFlagReason(f.reason()); }
        }
    }

    // ------------------------------------------------------------------ review

    @Transactional
    public TxnView updateTransaction(String farmerId, Long txnId, TxnUpdate u) {
        StatementTransaction t = txns.findById(txnId).filter(x -> x.getFarmerId().equals(farmerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        BankStatement s = statements.findById(t.getStatementId()).orElseThrow();
        if (!"REVIEW".equals(s.getStatus())) throw conflict("This statement is already confirmed. Undo the import first to change it.");
        if (u.category() != null) {
            ExpenseCategory c = ExpenseCategory.parse(u.category());
            if (c == null) throw bad("Unknown category");
            if (!TransactionCategorizer.directionFits(c, t.getAmount().signum() < 0)) {
                throw bad(t.getAmount().signum() < 0 ? "Money going out needs an expense category" : "Money coming in needs an income category");
            }
            List<StatementTransaction> targets = new ArrayList<>(List.of(t));
            if (Boolean.TRUE.equals(u.applyToSimilar())) {
                String key = TransactionCategorizer.merchantKey(t.getDescription());
                for (StatementTransaction o : txns.findByStatementIdOrderByTxnDateAscIdAsc(s.getId())) {
                    if (!o.getId().equals(t.getId()) && key.equals(TransactionCategorizer.merchantKey(o.getDescription()))
                            && TransactionCategorizer.directionFits(c, o.getAmount().signum() < 0)) targets.add(o);
                }
            }
            for (StatementTransaction x : targets) {
                x.setCategory(c.name());
                x.setConfidence(1.0);
                x.setCategorySource("USER");
                x.setNeedsReview("DUPLICATE".equals(x.getFlag()));
            }
            txns.saveAll(targets);
            remember(farmerId, t.getDescription(), c);
        }
        if (u.crop() != null) t.setCrop(u.crop().isBlank() ? null : clip(u.crop().trim(), 60));
        if (Boolean.TRUE.equals(u.clearFlag())) {
            t.setFlag(null);
            t.setFlagReason(null);
            t.setNeedsReview(false);
        }
        txns.save(t);
        return view(t);
    }

    /** Every correction is remembered and becomes training data: the same merchant is categorised correctly next time. */
    private void remember(String farmerId, String description, ExpenseCategory c) {
        String key = TransactionCategorizer.merchantKey(description);
        if (key.isBlank()) return;
        CategoryMemory m = memory.findByFarmerIdAndMerchantKey(farmerId, clip(key, 100)).orElseGet(() -> {
            CategoryMemory n = new CategoryMemory();
            n.setFarmerId(farmerId);
            n.setMerchantKey(clip(key, 100));
            n.setUses(0);
            return n;
        });
        m.setCategory(c.name());
        m.setUses(m.getUses() + 1);
        memory.save(m);
    }

    /** Imports the reviewed transactions into My Finances (duplicates, transfers and personal spending are left out). */
    @Transactional
    public StatementDetail confirm(String farmerId, String statementId) {
        BankStatement s = owned(statementId, farmerId);
        if (!"REVIEW".equals(s.getStatus())) throw conflict("Only a statement waiting for review can be confirmed");
        int imported = 0;
        for (StatementTransaction t : txns.findByStatementIdOrderByTxnDateAscIdAsc(statementId)) {
            ExpenseCategory c = ExpenseCategory.parse(t.getCategory());
            if (c == null || !c.countsInLedger() || "DUPLICATE".equals(t.getFlag()) || t.getMatchedOrderId() != null) continue;
            String item = clip(t.getDescription(), 150);
            if (t.getAmount().signum() < 0) {
                Expense e = new Expense();
                e.setFarmerId(farmerId);
                e.setItem(item);
                e.setCategory(c.label());
                e.setAmount(t.getAmount().abs());
                e.setDate(t.getTxnDate());
                e.setCrop(t.getCrop());
                e.setSource("STATEMENT");
                t.setLedgerType("EXPENSE");
                t.setLedgerId(expenses.save(e).getId());
            } else {
                Income i = new Income();
                i.setFarmerId(farmerId);
                i.setItem(item);
                i.setAmount(t.getAmount());
                i.setDate(t.getTxnDate());
                i.setSource("STATEMENT");
                t.setLedgerType("INCOME");
                t.setLedgerId(incomes.save(i).getId());
            }
            txns.save(t);
            imported++;
        }
        s.setStatus("CONFIRMED");
        s.setConfirmedAt(LocalDateTime.now());
        statements.save(s);
        audit.record(farmerId, "STATEMENT_CONFIRMED", "STATEMENT", statementId, imported + " ledger entries");
        return detail(statementId, farmerId);
    }

    /** Undo: removes the ledger entries this statement created and puts it back into review. */
    @Transactional
    public StatementDetail reopen(String farmerId, String statementId) {
        BankStatement s = owned(statementId, farmerId);
        if (!"CONFIRMED".equals(s.getStatus())) throw conflict("Only a confirmed statement can be reopened");
        removeLedgerEntries(statementId);
        s.setStatus("REVIEW");
        s.setConfirmedAt(null);
        statements.save(s);
        audit.record(farmerId, "STATEMENT_REOPENED", "STATEMENT", statementId, null);
        return detail(statementId, farmerId);
    }

    @Transactional
    public void delete(String farmerId, String statementId, boolean keepLedger) {
        BankStatement s = owned(statementId, farmerId);
        if (!keepLedger) removeLedgerEntries(statementId);
        txns.deleteByStatementId(statementId);
        if (s.getOriginalDocumentId() != null) {
            try { vault.delete(s.getOriginalDocumentId(), farmerId); } catch (ResponseStatusException ignored) { }
        }
        statements.delete(s);
        audit.record(farmerId, "STATEMENT_DELETED", "STATEMENT", statementId, keepLedger ? "ledger kept" : "ledger removed");
    }

    private void removeLedgerEntries(String statementId) {
        for (StatementTransaction t : txns.findByStatementIdOrderByTxnDateAscIdAsc(statementId)) {
            if (t.getLedgerId() == null) continue;
            if ("EXPENSE".equals(t.getLedgerType())) expenses.findById(t.getLedgerId()).ifPresent(expenses::delete);
            else if ("INCOME".equals(t.getLedgerType())) incomes.findById(t.getLedgerId()).ifPresent(incomes::delete);
            t.setLedgerId(null);
            t.setLedgerType(null);
            txns.save(t);
        }
    }

    // ------------------------------------------------------------------ reading and analysis

    @Transactional(readOnly = true)
    public List<StatementView> list(String farmerId) {
        return statements.findByFarmerIdOrderByCreatedAtDesc(farmerId).stream()
                .map(s -> statementView(s, txns.findByStatementIdOrderByTxnDateAscIdAsc(s.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public StatementDetail detail(String statementId, String farmerId) {
        BankStatement s = owned(statementId, farmerId);
        List<StatementTransaction> list = txns.findByStatementIdOrderByTxnDateAscIdAsc(statementId);
        Map<String, String> cats = new LinkedHashMap<>();
        for (ExpenseCategory c : ExpenseCategory.values()) cats.put(c.name(), c.label());
        return new StatementDetail(statementView(s, list), list.stream().map(this::view).toList(),
                new ArrayList<>(companion.hectaresByCrop(farmerId).keySet()), cats);
    }

    @Transactional(readOnly = true)
    public Analysis analysis(String farmerId) {
        List<StatementTransaction> confirmed = txns.confirmed(farmerId);
        List<ExpenseAnalyzer.Txn> list = confirmed.stream().filter(t -> !"DUPLICATE".equals(t.getFlag()))
                .map(t -> toAnalyzer(t, t.getId())).toList();
        Map<String, BigDecimal> hectares = companion.hectaresByCrop(farmerId);
        List<RuleView> ruleViews = rules(farmerId);
        List<ExpenseAnalyzer.AllocationRule> ar = ruleViews.stream()
                .map(r -> new ExpenseAnalyzer.AllocationRule(ExpenseCategory.parse(r.category()), r.method(), r.crop())).toList();
        ExpenseAnalyzer.Report report = ExpenseAnalyzer.analyse(list, hectares, ar);
        return new Analysis(report, hectares, ruleViews, statements.countByFarmerId(farmerId), confirmed.size());
    }

    public List<RuleView> rules(String farmerId) {
        return rules.findByFarmerId(farmerId).stream().map(r -> {
            ExpenseCategory c = ExpenseCategory.parse(r.getCategory());
            return new RuleView(r.getId(), r.getCategory(), c == null ? r.getCategory() : c.label(), r.getMethod(), r.getCrop());
        }).toList();
    }

    @Transactional
    public List<RuleView> saveRule(String farmerId, RuleRequest req) {
        ExpenseCategory c = ExpenseCategory.parse(req == null ? null : req.category());
        if (c == null || !c.isFarmExpense()) throw bad("Choose a farm expense category");
        String method = req.method() == null ? "" : req.method().trim().toUpperCase();
        if (!Set.of("BY_HECTARES", "FIXED_CROP").contains(method)) throw bad("Method must be BY_HECTARES or FIXED_CROP");
        if ("FIXED_CROP".equals(method) && (req.crop() == null || req.crop().isBlank())) throw bad("Choose the crop");
        AllocationRule r = rules.findByFarmerIdAndCategory(farmerId, c.name()).orElseGet(AllocationRule::new);
        r.setFarmerId(farmerId);
        r.setCategory(c.name());
        r.setMethod(method);
        r.setCrop("FIXED_CROP".equals(method) ? clip(req.crop().trim(), 60) : null);
        rules.save(r);
        return rules(farmerId);
    }

    @Transactional
    public List<RuleView> deleteRule(String farmerId, Long id) {
        rules.findById(id).filter(r -> r.getFarmerId().equals(farmerId)).ifPresent(rules::delete);
        return rules(farmerId);
    }

    public byte[] workbook(String farmerId) {
        Analysis a = analysis(farmerId);
        ExpenseAnalyzer.Report r = a.report();
        List<List<Object>> summary = new ArrayList<>();
        summary.add(List.of("Measure", "Value (R)"));
        summary.add(List.of("Farm income", r.totalIncome()));
        summary.add(List.of("Farm expenses", r.totalExpenses()));
        summary.add(List.of("Net farm result", r.totalIncome().subtract(r.totalExpenses())));
        summary.add(List.of("Personal / non-farm spending (excluded)", r.personalSpend()));
        summary.add(List.of("Costs not linked to a crop", r.unallocated()));
        for (ExpenseAnalyzer.Insight i : r.insights()) summary.add(Arrays.asList(i.text(), null));
        List<List<Object>> months = new ArrayList<>();
        months.add(List.of("Month", "Income (R)", "Expenses (R)", "Net (R)"));
        r.months().forEach(m -> months.add(List.of(m.month(), m.income(), m.expenses(), m.net())));
        List<List<Object>> cats = new ArrayList<>();
        cats.add(List.of("Category", "Amount (R)", "Share of farm costs", "Transactions"));
        r.categories().forEach(c -> cats.add(List.of(c.label(), c.amount(), c.share(), c.count())));
        List<List<Object>> crops = new ArrayList<>();
        crops.add(List.of("Crop", "Direct costs (R)", "Allocated costs (R)", "Total (R)", "Hectares", "Cost per hectare (R)"));
        r.crops().forEach(c -> crops.add(Arrays.asList(c.crop(), c.direct(), c.allocated(), c.total(), c.hectares(), c.perHectare())));
        List<List<Object>> lines = new ArrayList<>();
        lines.add(List.of("Date", "Description", "Amount (R)", "Category", "Crop", "Flag", "Categorised by"));
        for (StatementTransaction t : txns.confirmed(farmerId)) {
            ExpenseCategory c = ExpenseCategory.parse(t.getCategory());
            lines.add(Arrays.asList(t.getTxnDate(), t.getDescription(), t.getAmount(), c == null ? t.getCategory() : c.label(),
                    t.getCrop(), t.getFlag(), t.getCategorySource()));
        }
        return new SimpleXlsxWriter().sheet("Summary", summary).sheet("Monthly", months).sheet("Categories", cats)
                .sheet("Cost per crop", crops).sheet("Transactions", lines).toBytes();
    }

    public String csv(String farmerId) {
        StringBuilder sb = new StringBuilder("﻿Date,Description,Amount,Category,Crop,Flag,Source\r\n");
        for (StatementTransaction t : txns.confirmed(farmerId)) {
            ExpenseCategory c = ExpenseCategory.parse(t.getCategory());
            sb.append(t.getTxnDate()).append(',').append(q(t.getDescription())).append(',').append(t.getAmount().toPlainString())
                    .append(',').append(q(c == null ? t.getCategory() : c.label())).append(',').append(q(t.getCrop()))
                    .append(',').append(q(t.getFlag())).append(',').append(q(t.getCategorySource())).append("\r\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ helpers

    private TransactionCategorizer categorizerFor(String farmerId) {
        Map<String, ExpenseCategory> learned = new HashMap<>();
        for (CategoryMemory m : memory.findByFarmerId(farmerId)) {
            ExpenseCategory c = ExpenseCategory.parse(m.getCategory());
            if (c != null) learned.put(m.getMerchantKey(), c);
        }
        List<Map.Entry<String, ExpenseCategory>> history = new ArrayList<>();
        for (StatementTransaction t : txns.confirmedHistory(farmerId, PageRequest.of(0, 3000))) {
            ExpenseCategory c = ExpenseCategory.parse(t.getCategory());
            if (c != null && !"DEFAULT".equals(t.getCategorySource())) history.add(Map.entry(t.getDescription(), c));
        }
        return new TransactionCategorizer(learned, history);
    }

    private static ExpenseAnalyzer.Txn toAnalyzer(StatementTransaction t, long id) {
        return new ExpenseAnalyzer.Txn(id, t.getTxnDate(), t.getDescription(), t.getAmount(),
                ExpenseCategory.parse(t.getCategory()), t.getCrop());
    }

    private StatementView statementView(BankStatement s, List<StatementTransaction> list) {
        BigDecimal in = BigDecimal.ZERO, out = BigDecimal.ZERO;
        long review = 0, flagged = 0;
        for (StatementTransaction t : list) {
            if (t.getAmount().signum() > 0) in = in.add(t.getAmount()); else out = out.add(t.getAmount().abs());
            if (t.isNeedsReview()) review++;
            if (t.getFlag() != null) flagged++;
        }
        return new StatementView(s.getId(), s.getFileName(), s.getSourceType(), s.getExtractionMethod(), s.getBank(),
                s.getAccountHint(), s.getPeriodStart(), s.getPeriodEnd(), s.getOpeningBalance(), s.getClosingBalance(),
                s.getComputedClosing(), s.getBalanceCheck(), s.getStatus(), s.getMessage(), s.getTransactionCount(),
                review, flagged, in, out, s.getOriginalDocumentId() != null, s.getCreatedAt(), s.getConfirmedAt());
    }

    private TxnView view(StatementTransaction t) {
        ExpenseCategory c = ExpenseCategory.parse(t.getCategory());
        return new TxnView(t.getId(), t.getTxnDate(), t.getDescription(), t.getAmount(), t.getBalance(), t.getCategory(),
                c == null ? t.getCategory() : c.label(), t.getConfidence(), t.getCategorySource(), t.isNeedsReview(),
                t.isDirectionCertain(), t.getCrop(), t.getFlag(), t.getFlagReason(), t.getMatchedOrderId(), t.getLedgerType());
    }

    private BankStatement owned(String id, String farmerId) {
        BankStatement s = statements.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found"));
        if (!s.getFarmerId().equals(farmerId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Statement not found");
        return s;
    }

    static String detectType(byte[] b, String fileName) {
        if (b.length > 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "PDF";
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8) return "IMAGE";
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "IMAGE";
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (n.endsWith(".csv") || n.endsWith(".txt") || n.endsWith(".tsv")) return "CSV";
        // No extension: treat printable text as CSV.
        int check = Math.min(b.length, 2048);
        for (int i = 0; i < check; i++) {
            int c = b[i] & 0xFF;
            if (c == 0 || (c < 0x09) || (c > 0x0D && c < 0x20)) return null;
        }
        return "CSV";
    }

    /** Bank exports are UTF-8 or Windows-1252; fall back to the latter when UTF-8 is not valid. */
    static String decode(byte[] bytes) {
        var decoder = StandardCharsets.UTF_8.newDecoder();
        try {
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }

    private static String q(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

    private static ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }
}
