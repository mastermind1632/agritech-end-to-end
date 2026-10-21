package tut.ac.za.AgriFinanceAPIs.statements;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a bank statement into transactions. Pure Java (no Spring) so it can be unit tested in isolation.
 *
 * Two inputs are supported:
 *  - CSV exports (the most accurate option; every major South African bank offers one), and
 *  - text lines extracted from a PDF, either from its text layer or from OCR (done by the document-service).
 *
 * The parser is deliberately layout-tolerant rather than one hard-coded parser per bank: it finds the date at
 * the start of a line and the money columns at the end. When a running balance is present, the change in balance
 * decides whether a line is money in or money out, which is far more reliable than reading "Cr"/"Dr" markers.
 */
public final class StatementParser {

    public record ParsedTxn(LocalDate date, String description, BigDecimal amount, BigDecimal balance,
                            boolean directionCertain) {
        /** amount is signed: negative = money out (debit), positive = money in (credit). */
        public boolean isDebit() { return amount.signum() < 0; }
    }

    public record ParsedStatement(String bank, String accountHint, BigDecimal openingBalance, BigDecimal closingBalance,
                                  LocalDate periodStart, LocalDate periodEnd, List<ParsedTxn> transactions,
                                  List<String> warnings) {

        /** opening + all transactions should equal closing. This single check catches most extraction errors. */
        public String balanceCheck() {
            if (openingBalance == null || closingBalance == null) return "UNKNOWN";
            return computedClosing().subtract(closingBalance).abs().compareTo(new BigDecimal("0.01")) <= 0 ? "PASSED" : "FAILED";
        }

        public BigDecimal computedClosing() {
            if (openingBalance == null) return null;
            BigDecimal total = openingBalance;
            for (ParsedTxn t : transactions) total = total.add(t.amount());
            return total.setScale(2, RoundingMode.HALF_UP);
        }
    }

    private static final Map<String, String> BANK_MARKERS = new LinkedHashMap<>();
    static {
        BANK_MARKERS.put("first national bank", "FNB");
        BANK_MARKERS.put("fnb", "FNB");
        BANK_MARKERS.put("standard bank", "Standard Bank");
        BANK_MARKERS.put("absa", "Absa");
        BANK_MARKERS.put("nedbank", "Nedbank");
        BANK_MARKERS.put("capitec", "Capitec");
        BANK_MARKERS.put("tymebank", "TymeBank");
        BANK_MARKERS.put("african bank", "African Bank");
        BANK_MARKERS.put("investec", "Investec");
        BANK_MARKERS.put("discovery bank", "Discovery Bank");
        BANK_MARKERS.put("land bank", "Land Bank");
    }

    private static final Pattern YEAR = Pattern.compile("\\b(20\\d{2})\\b");
    private static final Pattern ACCOUNT = Pattern.compile("(?i)(?:account|acc|rekening)\\s*(?:number|no|nr|#)?\\s*[:.]?\\s*(\\d[\\d -]{5,20}\\d)");
    private static final Pattern OPENING = Pattern.compile("(?i)(opening\\s+balance|balance\\s+brought\\s+forward|brought\\s+forward|balance\\s+b/?f|beginning\\s+balance|openingsaldo|vorige\\s+saldo)");
    private static final Pattern CLOSING = Pattern.compile("(?i)(closing\\s+balance|balance\\s+carried\\s+forward|carried\\s+forward|balance\\s+c/?f|ending\\s+balance|eindsaldo|afsluitingsaldo)");

    private static final Pattern DATE_ISO = Pattern.compile("^(\\d{4}[/.-]\\d{1,2}[/.-]\\d{1,2})\\s+(.*)$");
    private static final Pattern DATE_NUM = Pattern.compile("^(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4})\\s+(.*)$");
    private static final Pattern DATE_TEXT = Pattern.compile("^(\\d{1,2}\\s+[A-Za-z]{3,9}\\.?(?:\\s+\\d{4}|\\s+\\d{2}(?=\\s))?)\\s+(.*)$");
    private static final Pattern DATE_COMPACT = Pattern.compile("^(\\d{8})\\s+(.*)$");

    /** A money token: optional sign/currency, digits with optional thousands separators, exactly two decimals. */
    private static final Pattern MONEY = Pattern.compile(
            "^[-+]?R?\\(?-?\\d{1,3}(?:[ ,\\u00A0']?\\d{3})*[.,]\\d{2}\\)?-?(?:cr|dr|CR|DR|Cr|Dr)?$");
    private static final Pattern THREE_DIGITS_TAIL = Pattern.compile("^\\d{3}(?:[.,]\\d{2})?\\)?-?(?:cr|dr|CR|DR|Cr|Dr)?$");
    private static final Pattern SMALL_INT = Pattern.compile("^[-+]?R?\\(?\\d{1,3}$");

    private static final Set<String> CREDIT_WORDS = Set.of("deposit", "payment received", "credit transfer", "salary received",
            "inward", "received", "refund", "interest earned", "inbetaling");

    private StatementParser() { }

    // ------------------------------------------------------------------ CSV

    public static ParsedStatement parseCsv(String csv) {
        List<String> warnings = new ArrayList<>();
        String text = csv.replace("﻿", "");
        String[] rawLines = text.split("\\r?\\n");
        char delimiter = detectDelimiter(rawLines);
        List<List<String>> rows = new ArrayList<>();
        for (String line : rawLines) {
            if (line.isBlank()) continue;
            rows.add(splitCsvLine(line, delimiter));
        }
        String bank = detectBank(text);
        String account = detectAccount(text);
        Integer yearHint = yearHint(text);

        int headerIndex = -1;
        Map<String, Integer> cols = Map.of();
        for (int i = 0; i < Math.min(rows.size(), 40); i++) {
            Map<String, Integer> found = mapHeader(rows.get(i));
            if (found.containsKey("date") && (found.containsKey("amount") || found.containsKey("debit") || found.containsKey("credit"))) {
                headerIndex = i;
                cols = found;
                break;
            }
        }
        if (headerIndex < 0) {
            cols = guessColumns(rows, yearHint);
            if (!cols.containsKey("date") || !cols.containsKey("amount")) {
                warnings.add("Could not find date and amount columns in the CSV file");
                return new ParsedStatement(bank, account, null, null, null, null, List.of(), warnings);
            }
            warnings.add("No header row found; columns were guessed from the data");
        } else if (bank == null) {
            // Export files often omit the bank's name, but their column layout gives it away.
            String header = String.join(",", rows.get(headerIndex)).toLowerCase(Locale.ROOT);
            if (header.contains("parent category") && header.contains("money in")) bank = "Capitec";
            else if (header.contains("money in") && header.contains("money out")) bank = "TymeBank";
        }

        BigDecimal opening = null, closing = null;
        List<ParsedTxn> txns = new ArrayList<>();
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            String joined = String.join(" ", r);
            if (OPENING.matcher(joined).find()) { BigDecimal b = lastMoney(r); if (b != null) opening = b; continue; }
            if (CLOSING.matcher(joined).find()) { BigDecimal b = lastMoney(r); if (b != null) closing = b; continue; }
            LocalDate date = parseDate(cell(r, cols.get("date")), yearHint);
            if (date == null) continue;
            BigDecimal amount = null;
            boolean certain = true;
            if (cols.containsKey("amount")) {
                amount = parseAmount(cell(r, cols.get("amount")));
            }
            if (amount == null && (cols.containsKey("debit") || cols.containsKey("credit"))) {
                BigDecimal debit = abs(parseAmount(cell(r, cols.get("debit"))));
                BigDecimal credit = abs(parseAmount(cell(r, cols.get("credit"))));
                BigDecimal fee = abs(parseAmount(cell(r, cols.get("fee"))));
                if (debit == null && credit == null && fee == null) continue;
                amount = zero(credit).subtract(zero(debit)).subtract(zero(fee));
            } else if (amount != null && cols.containsKey("fee")) {
                BigDecimal fee = abs(parseAmount(cell(r, cols.get("fee"))));
                if (fee != null) amount = amount.subtract(fee);
            }
            if (amount == null || amount.signum() == 0) continue;
            String desc = cell(r, cols.get("description"));
            if (desc == null || desc.isBlank()) desc = cell(r, cols.get("reference"));
            BigDecimal balance = parseAmount(cell(r, cols.get("balance")));
            txns.add(new ParsedTxn(date, clean(desc), amount.setScale(2, RoundingMode.HALF_UP), balance, certain));
        }
        txns = orderChronologically(txns);
        return finish(bank, account, opening, closing, txns, warnings);
    }

    // ------------------------------------------------------------------ text (PDF / OCR)

    public static ParsedStatement parseText(List<String> lines) {
        List<String> warnings = new ArrayList<>();
        String all = String.join("\n", lines);
        String bank = detectBank(all);
        String account = detectAccount(all);
        Integer yearHint = yearHint(all);

        BigDecimal opening = null, closing = null;
        List<ParsedTxn> txns = new ArrayList<>();
        BigDecimal prevBalance = null;
        LocalDate prevDate = null;
        int year = yearHint == null ? LocalDate.now().getYear() : yearHint;
        StringBuilder pendingDesc = null;

        for (String raw : lines) {
            String line = raw.replace(' ', ' ').replaceAll("\\s+", " ").trim();
            if (line.isEmpty()) continue;

            if (OPENING.matcher(line).find()) {
                List<BigDecimal> m = trailingMoney(tokens(line)).amounts();
                if (!m.isEmpty()) { opening = m.get(m.size() - 1); prevBalance = opening; }
                continue;
            }
            if (CLOSING.matcher(line).find()) {
                List<BigDecimal> m = trailingMoney(tokens(line)).amounts();
                if (!m.isEmpty()) closing = m.get(m.size() - 1);
                continue;
            }

            String[] dateAndRest = splitLeadingDate(line);
            if (dateAndRest == null) {
                // A line with no date and no amounts usually continues the previous description.
                if (pendingDesc != null && !txns.isEmpty() && trailingMoney(tokens(line)).amounts().isEmpty()
                        && line.length() < 80 && pendingDesc.length() < 200) {
                    pendingDesc.append(' ').append(line);
                    ParsedTxn last = txns.remove(txns.size() - 1);
                    txns.add(new ParsedTxn(last.date(), clean(pendingDesc.toString()), last.amount(), last.balance(), last.directionCertain()));
                }
                continue;
            }
            LocalDate date = parseDate(dateAndRest[0], year);
            if (date == null) continue;
            if (prevDate != null && prevDate.getMonthValue() == 12 && date.getMonthValue() == 1 && !hasYear(dateAndRest[0])) {
                year++;
                date = date.withYear(year);
            }
            Tail tail = trailingMoney(tokens(dateAndRest[1]));
            if (tail.amounts().isEmpty()) continue;
            String description = clean(tail.description());
            List<BigDecimal> amounts = tail.amounts();
            List<Boolean> explicitSign = tail.explicitSign();

            BigDecimal balance = null;
            BigDecimal amount;
            boolean certain = false;
            if (amounts.size() >= 2) {
                balance = amounts.get(amounts.size() - 1);
                amount = amounts.get(amounts.size() - 2);
                if (prevBalance != null) {
                    BigDecimal delta = balance.subtract(prevBalance);
                    BigDecimal match = null;
                    for (int i = amounts.size() - 2; i >= 0; i--) {
                        if (amounts.get(i).abs().compareTo(delta.abs()) == 0) { match = amounts.get(i); break; }
                    }
                    if (match == null && amounts.size() >= 3) {
                        // Two money columns before the balance (e.g. amount + fee) can explain the change together.
                        BigDecimal sum = amounts.get(amounts.size() - 2).abs().add(amounts.get(amounts.size() - 3).abs());
                        if (sum.compareTo(delta.abs()) == 0) match = sum;
                    }
                    if (match != null) {
                        amount = delta.signum() < 0 ? match.abs().negate() : match.abs();
                        certain = true;
                    } else {
                        warnings.add("Balance did not reconcile on " + date + " (" + description + ")");
                    }
                }
                if (!certain && !explicitSign.get(amounts.size() - 2)) {
                    amount = guessSign(amount, description);
                }
                prevBalance = balance;
            } else {
                amount = amounts.get(0);
                certain = explicitSign.get(0);
                if (!certain) amount = guessSign(amount, description);
            }
            if (amount.signum() == 0) continue;
            txns.add(new ParsedTxn(date, description, amount.setScale(2, RoundingMode.HALF_UP), balance, certain));
            pendingDesc = new StringBuilder(description);
            prevDate = date;
        }
        if (txns.isEmpty()) warnings.add("No transaction lines were recognised");
        return finish(bank, account, opening, closing, txns, warnings);
    }

    // ------------------------------------------------------------------ shared helpers

    private static ParsedStatement finish(String bank, String account, BigDecimal opening, BigDecimal closing,
                                          List<ParsedTxn> txns, List<String> warnings) {
        if (opening == null && !txns.isEmpty() && txns.get(0).balance() != null) {
            opening = txns.get(0).balance().subtract(txns.get(0).amount());
        }
        if (closing == null && !txns.isEmpty() && txns.get(txns.size() - 1).balance() != null) {
            closing = txns.get(txns.size() - 1).balance();
        }
        LocalDate start = txns.stream().map(ParsedTxn::date).min(LocalDate::compareTo).orElse(null);
        LocalDate end = txns.stream().map(ParsedTxn::date).max(LocalDate::compareTo).orElse(null);
        return new ParsedStatement(bank, account, opening, closing, start, end, List.copyOf(txns), List.copyOf(warnings));
    }

    /** Some exports list newest first. Balances only reconcile in date order, so flip those. */
    private static List<ParsedTxn> orderChronologically(List<ParsedTxn> txns) {
        if (txns.size() < 2) return txns;
        if (txns.get(0).date().isAfter(txns.get(txns.size() - 1).date())) {
            List<ParsedTxn> copy = new ArrayList<>(txns);
            Collections.reverse(copy);
            return copy;
        }
        return txns;
    }

    private static BigDecimal guessSign(BigDecimal amount, String description) {
        if (amount.signum() < 0) return amount;
        String d = description == null ? "" : description.toLowerCase(Locale.ROOT);
        for (String w : CREDIT_WORDS) if (d.contains(w)) return amount.abs();
        return amount.abs().negate();
    }

    static String detectBank(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : BANK_MARKERS.entrySet()) {
            if (Pattern.compile("\\b" + Pattern.quote(e.getKey()) + "\\b").matcher(t).find()) return e.getValue();
        }
        return null;
    }

    static String detectAccount(String text) {
        Matcher m = ACCOUNT.matcher(text);
        if (!m.find()) return null;
        String digits = m.group(1).replaceAll("\\D", "");
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : null;
    }

    static Integer yearHint(String text) {
        Map<Integer, Integer> counts = new HashMap<>();
        Matcher m = YEAR.matcher(text);
        while (m.find()) counts.merge(Integer.parseInt(m.group(1)), 1, Integer::sum);
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private static boolean hasYear(String dateText) {
        return dateText.matches(".*\\d{4}.*") || dateText.matches("^\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2}$");
    }

    private static String[] splitLeadingDate(String line) {
        for (Pattern p : List.of(DATE_ISO, DATE_NUM, DATE_TEXT, DATE_COMPACT)) {
            Matcher m = p.matcher(line);
            if (m.matches()) return new String[] { m.group(1), m.group(2) };
        }
        return null;
    }

    private static List<String> tokens(String s) {
        return new ArrayList<>(Arrays.asList(s.trim().split(" ")));
    }

    private record Tail(String description, List<BigDecimal> amounts, List<Boolean> explicitSign) { }

    /** Reads money values from the end of a line, re-joining numbers split by a space thousands separator. */
    private static Tail trailingMoney(List<String> toks) {
        LinkedList<BigDecimal> amounts = new LinkedList<>();
        LinkedList<Boolean> signs = new LinkedList<>();
        int i = toks.size() - 1;
        while (i >= 0) {
            String t = toks.get(i);
            // "Cr"/"Dr" written as a separate word after the number.
            String suffix = "";
            if ((t.equalsIgnoreCase("cr") || t.equalsIgnoreCase("dr")) && i > 0) {
                suffix = t;
                i--;
                t = toks.get(i);
            }
            String joined = t;
            int j = i;
            // Join "1 234 567.89" style numbers: walk left while the left token is a 1-3 digit group.
            while (j > 0 && THREE_DIGITS_TAIL.matcher(joined.replaceFirst("^[-+]", "")).matches()
                    && SMALL_INT.matcher(toks.get(j - 1)).matches()
                    && !(amounts.isEmpty() && j - 1 == 0)) {
                String candidate = toks.get(j - 1) + joined;
                if (!MONEY.matcher(candidate).matches()) break;
                joined = candidate;
                j--;
            }
            String money = joined + suffix;
            if (!MONEY.matcher(money.replace(" ", "")).matches()) {
                if (!suffix.isEmpty()) i++;
                break;
            }
            BigDecimal v = parseAmount(money);
            if (v == null) break;
            amounts.addFirst(v);
            signs.addFirst(hasExplicitSign(money));
            i = j - 1;
        }
        String desc = String.join(" ", toks.subList(0, Math.max(0, i + 1)));
        return new Tail(desc, amounts, signs);
    }

    private static boolean hasExplicitSign(String token) {
        String t = token.trim();
        return t.startsWith("-") || t.endsWith("-") || t.startsWith("(") || t.toLowerCase(Locale.ROOT).endsWith("cr")
                || t.toLowerCase(Locale.ROOT).endsWith("dr") || t.startsWith("+");
    }

    /** Parses South African and international number formats. Returns null when the text is not an amount. */
    public static BigDecimal parseAmount(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replace(' ', ' ');
        if (s.isEmpty()) return null;
        boolean negative = false;
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.endsWith("cr")) { s = s.substring(0, s.length() - 2).trim(); }
        else if (lower.endsWith("dr")) { negative = true; s = s.substring(0, s.length() - 2).trim(); }
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length() - 1); }
        if (s.endsWith("-")) { negative = true; s = s.substring(0, s.length() - 1); }
        if (s.startsWith("+")) s = s.substring(1);
        if (s.startsWith("-")) { negative = true; s = s.substring(1); }
        s = s.replaceFirst("^(?i)(zar|r)\\s?", "");
        if (s.startsWith("-")) { negative = true; s = s.substring(1); }
        s = s.replace(" ", "").replace("'", "");
        if (!s.matches("[\\d.,]+") || !s.matches(".*\\d.*")) return null;
        int lastDot = s.lastIndexOf('.');
        int lastComma = s.lastIndexOf(',');
        String normalized;
        if (lastDot >= 0 && lastComma >= 0) {
            if (lastComma > lastDot) normalized = s.replace(".", "").replace(',', '.');
            else normalized = s.replace(",", "");
        } else if (lastComma >= 0) {
            int decimals = s.length() - lastComma - 1;
            normalized = (decimals == 2 && s.indexOf(',') == lastComma) ? s.replace(',', '.') : s.replace(",", "");
        } else if (lastDot >= 0 && s.indexOf('.') != lastDot) {
            normalized = s.replace(".", ""); // 1.234.567 style thousands
        } else {
            normalized = s;
        }
        try {
            BigDecimal v = new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
            return negative ? v.negate() : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static final List<DateTimeFormatter> FORMATS = new ArrayList<>();
    private static final List<DateTimeFormatter> FORMATS_NO_YEAR = new ArrayList<>();
    static {
        for (String p : List.of("yyyy-M-d", "yyyy/M/d", "yyyy.M.d", "d/M/yyyy", "d-M-yyyy", "d.M.yyyy", "d/M/yy", "d-M-yy",
                "yyyyMMdd", "d MMM yyyy", "d MMMM yyyy", "d MMM yy")) {
            FORMATS.add(new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p).toFormatter(Locale.ENGLISH));
        }
        for (String p : List.of("d MMM", "d MMMM")) {
            FORMATS_NO_YEAR.add(new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p)
                    .parseDefaulting(ChronoField.YEAR, 2000).toFormatter(Locale.ENGLISH));
        }
    }

    private static final Map<String, String> AFRIKAANS_MONTHS = Map.of(
            "mei", "May", "okt", "Oct", "des", "Dec", "mrt", "Mar", "maa", "Mar", "januarie", "January",
            "februarie", "February", "maart", "March", "junie", "June", "julie", "July");

    public static LocalDate parseDate(String raw, Integer yearHint) {
        if (raw == null) return null;
        String s = raw.trim().replace(".", raw.matches(".*[A-Za-z].*") ? "" : ".");
        if (s.isEmpty()) return null;
        String[] parts = s.split(" ");
        if (parts.length >= 2) {
            String repl = AFRIKAANS_MONTHS.get(parts[1].toLowerCase(Locale.ROOT));
            if (repl != null) { parts[1] = repl; s = String.join(" ", parts); }
            if (parts[1].length() > 3 && parts[1].length() < 9 && !isFullMonth(parts[1])) {
                parts[1] = parts[1].substring(0, 3);
                s = String.join(" ", parts);
            }
        }
        for (DateTimeFormatter f : FORMATS) {
            try {
                LocalDate d = LocalDate.parse(s, f);
                if (d.getYear() < 100) d = d.plusYears(2000);
                if (d.getYear() > 1990 && d.getYear() < 2100) return d;
            } catch (DateTimeParseException ignored) { }
        }
        for (DateTimeFormatter f : FORMATS_NO_YEAR) {
            try {
                LocalDate d = LocalDate.parse(s, f);
                return d.withYear(yearHint == null ? LocalDate.now().getYear() : yearHint);
            } catch (java.time.DateTimeException ignored) { }
        }
        return null;
    }

    private static boolean isFullMonth(String s) {
        return Set.of("january", "february", "march", "april", "may", "june", "july", "august", "september",
                "october", "november", "december").contains(s.toLowerCase(Locale.ROOT));
    }

    // ---- CSV helpers

    private static char detectDelimiter(String[] lines) {
        int commas = 0, semis = 0, tabs = 0;
        for (int i = 0; i < Math.min(lines.length, 30); i++) {
            String l = lines[i];
            commas += count(l, ','); semis += count(l, ';'); tabs += count(l, '\t');
        }
        if (tabs > commas && tabs > semis) return '\t';
        return semis > commas ? ';' : ',';
    }

    private static int count(String s, char c) {
        int n = 0;
        boolean quoted = false;
        for (char ch : s.toCharArray()) {
            if (ch == '"') quoted = !quoted;
            else if (ch == c && !quoted) n++;
        }
        return n;
    }

    static List<String> splitCsvLine(String line, char delimiter) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == delimiter && !quoted) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out;
    }

    private static Map<String, Integer> mapHeader(List<String> row) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < row.size(); i++) {
            String h = row.get(i).toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
            if (h.isEmpty()) continue;
            if ((h.equals("transaction date") || h.equals("date") || h.equals("datum") || h.equals("posting date")
                    || h.equals("value date") || h.equals("transaksie datum") || h.equals("trans date"))
                    && (!m.containsKey("date") || h.equals("transaction date"))) m.put("date", i);
            else if (h.equals("amount") || h.equals("bedrag") || h.equals("transaction amount") || h.equals("amount zar")) m.putIfAbsent("amount", i);
            else if (h.equals("debit") || h.equals("debits") || h.equals("money out") || h.equals("withdrawal") || h.equals("withdrawals") || h.equals("debiet")) m.putIfAbsent("debit", i);
            else if (h.equals("credit") || h.equals("credits") || h.equals("money in") || h.equals("deposit") || h.equals("deposits") || h.equals("krediet")) m.putIfAbsent("credit", i);
            else if (h.equals("fee") || h.equals("fees") || h.equals("bank charges") || h.equals("service fee")) m.putIfAbsent("fee", i);
            else if (h.equals("balance") || h.equals("saldo") || h.equals("running balance") || h.equals("available balance")) m.putIfAbsent("balance", i);
            else if (h.equals("description") || h.equals("details") || h.equals("narrative") || h.equals("transaction description")
                    || h.equals("beskrywing") || h.equals("transaction details") || h.equals("original description")) m.putIfAbsent("description", i);
            else if (h.equals("reference") || h.equals("verwysing") || h.equals("ref")) m.putIfAbsent("reference", i);
        }
        return m;
    }

    private static Map<String, Integer> guessColumns(List<List<String>> rows, Integer yearHint) {
        Map<String, Integer> m = new HashMap<>();
        List<List<String>> sample = rows.subList(0, Math.min(rows.size(), 20));
        int width = sample.stream().mapToInt(List::size).max().orElse(0);
        int bestText = -1, bestTextLen = -1;
        List<Integer> numeric = new ArrayList<>();
        for (int c = 0; c < width; c++) {
            int dates = 0, nums = 0, textLen = 0;
            for (List<String> r : sample) {
                String v = cell(r, c);
                if (v == null || v.isBlank()) continue;
                if (parseDate(v, yearHint) != null) dates++;
                else if (parseAmount(v) != null) nums++;
                else textLen += v.length();
            }
            if (dates >= sample.size() / 2 && !m.containsKey("date")) m.put("date", c);
            else if (nums >= sample.size() / 2) numeric.add(c);
            else if (textLen > bestTextLen) { bestTextLen = textLen; bestText = c; }
        }
        if (!numeric.isEmpty()) {
            m.put("amount", numeric.get(0));
            if (numeric.size() >= 2) m.put("balance", numeric.get(numeric.size() - 1));
        }
        if (bestText >= 0) m.put("description", bestText);
        return m;
    }

    private static String cell(List<String> r, Integer i) {
        if (i == null || i < 0 || i >= r.size()) return null;
        return r.get(i);
    }

    private static BigDecimal lastMoney(List<String> row) {
        for (int i = row.size() - 1; i >= 0; i--) {
            BigDecimal v = parseAmount(row.get(i));
            if (v != null) return v;
        }
        return null;
    }

    private static BigDecimal abs(BigDecimal v) { return v == null ? null : v.abs(); }

    private static BigDecimal zero(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static String clean(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }
}
