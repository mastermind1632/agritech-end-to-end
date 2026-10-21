package tut.ac.za.AgriFinanceAPIs.statements;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatementParserTest {

    @Test
    void parsesSouthAfricanAndInternationalAmounts() {
        assertEquals(new BigDecimal("1234.56"), StatementParser.parseAmount("1 234.56"));
        assertEquals(new BigDecimal("1234.56"), StatementParser.parseAmount("1,234.56"));
        assertEquals(new BigDecimal("1234.56"), StatementParser.parseAmount("1.234,56"));
        assertEquals(new BigDecimal("-450.00"), StatementParser.parseAmount("-450.00"));
        assertEquals(new BigDecimal("-450.00"), StatementParser.parseAmount("450.00Dr"));
        assertEquals(new BigDecimal("450.00"), StatementParser.parseAmount("450.00 Cr"));
        assertEquals(new BigDecimal("-99.90"), StatementParser.parseAmount("(99.90)"));
        assertEquals(new BigDecimal("-99.90"), StatementParser.parseAmount("99.90-"));
        assertEquals(new BigDecimal("12.50"), StatementParser.parseAmount("R12,50"));
        assertNull(StatementParser.parseAmount("Engen"));
        assertNull(StatementParser.parseAmount(""));
    }

    @Test
    void parsesCommonDateFormats() {
        assertEquals(LocalDate.of(2026, 3, 5), StatementParser.parseDate("2026/03/05", null));
        assertEquals(LocalDate.of(2026, 3, 5), StatementParser.parseDate("05/03/2026", null));
        assertEquals(LocalDate.of(2026, 3, 5), StatementParser.parseDate("5 Mar 2026", null));
        assertEquals(LocalDate.of(2026, 5, 5), StatementParser.parseDate("05 Mei 2026", null));
        assertEquals(LocalDate.of(2026, 3, 5), StatementParser.parseDate("05 Mar", 2026));
        assertEquals(LocalDate.of(2026, 3, 5), StatementParser.parseDate("20260305", null));
        assertNull(StatementParser.parseDate("Opening balance", 2026));
    }

    @Test
    void parsesCapitecStyleCsvWithMoneyInMoneyOutAndFees() {
        String csv = """
                Nr,Account,Posting Date,Transaction Date,Description,Original Description,Parent Category,Category,Money In,Money Out,Fee,Balance
                1,1234567890,2026/03/01,2026/03/01,Engen Polokwane,ENGEN POLOKWANE,Transport,Fuel,,-850.00,,4150.00
                2,1234567890,2026/03/02,2026/03/02,Joburg Market,JOBURG MARKET PAYMENT,Income,Other,6200.00,,,10350.00
                3,1234567890,2026/03/03,2026/03/03,Omnia Fertilizer,OMNIA FERTILIZER,Shopping,Other,,-2400.00,-5.00,7945.00
                """;
        StatementParser.ParsedStatement s = StatementParser.parseCsv(csv);
        assertEquals(3, s.transactions().size());
        assertEquals("Capitec", s.bank());
        assertEquals(new BigDecimal("-850.00"), s.transactions().get(0).amount());
        assertEquals(new BigDecimal("6200.00"), s.transactions().get(1).amount());
        assertEquals(new BigDecimal("-2405.00"), s.transactions().get(2).amount());
        assertEquals(new BigDecimal("5000.00"), s.openingBalance());
        assertEquals("PASSED", s.balanceCheck());
    }

    @Test
    void parsesSimpleAmountBalanceCsvNewestFirst() {
        String csv = """
                Date;Description;Amount;Balance
                2026-04-03;SERVICE FEE;-65,00;9 235,00
                2026-04-02;SALARY WORKERS;-3 000,00;9 300,00
                2026-04-01;GRANT DEPT OF AGRICULTURE;12 300,00;12 300,00
                """;
        StatementParser.ParsedStatement s = StatementParser.parseCsv(csv);
        assertEquals(3, s.transactions().size());
        assertEquals(LocalDate.of(2026, 4, 1), s.transactions().get(0).date(), "sorted oldest first");
        assertEquals(new BigDecimal("0.00"), s.openingBalance());
        assertEquals("PASSED", s.balanceCheck());
    }

    @Test
    void readsPdfTextLinesUsingBalanceToDecideDirection() {
        List<String> lines = List.of(
                "FIRST NATIONAL BANK",
                "Account Number : 6201 234 5678",
                "Statement Period : 01 March 2026 to 31 March 2026",
                "Opening Balance 12 500.00 Cr",
                "02 Mar POS Purchase Engen Tzaneen 1 250.00 11 250.00 Cr",
                "05 Mar Payment From Freshmark 8 400.00 19 650.00 Cr",
                "06 Mar Debit Order Santam Insurance 645.50 19 004.50 Cr",
                "#Monthly account fee 69.00 18 935.50 Cr",
                "28 Mar Wages March 4 000.00 14 935.50 Cr",
                "Closing Balance 14 935.50 Cr");
        StatementParser.ParsedStatement s = StatementParser.parseText(lines);
        assertEquals("FNB", s.bank());
        assertEquals("5678", s.accountHint());
        assertEquals(4, s.transactions().size(), "the fee line has no date so it is not a transaction");
        assertEquals(new BigDecimal("-1250.00"), s.transactions().get(0).amount());
        assertTrue(s.transactions().get(0).directionCertain());
        assertEquals(new BigDecimal("8400.00"), s.transactions().get(1).amount());
        assertEquals(LocalDate.of(2026, 3, 28), s.transactions().get(3).date());
        assertEquals(new BigDecimal("12500.00"), s.openingBalance());
        assertEquals(new BigDecimal("14935.50"), s.closingBalance());
        // The undated fee line was skipped, so the check correctly reports the gap.
        assertEquals("FAILED", s.balanceCheck());
    }

    @Test
    void validatesBalanceWhenAllLinesAreRead() {
        List<String> lines = List.of(
                "Capitec Bank",
                "Opening balance 1 000.00",
                "01/03/2026 Starke Ayres seeds -300.00 700.00",
                "02/03/2026 Deposit Farm Market sale 1 500.00 2 200.00",
                "Closing balance 2 200.00");
        StatementParser.ParsedStatement s = StatementParser.parseText(lines);
        assertEquals(2, s.transactions().size());
        assertEquals("PASSED", s.balanceCheck());
        assertEquals(new BigDecimal("2200.00"), s.computedClosing());
    }

    @Test
    void reportsMissingColumns() {
        StatementParser.ParsedStatement s = StatementParser.parseCsv("hello,world\nfoo,bar\n");
        assertTrue(s.transactions().isEmpty());
        assertFalse(s.warnings().isEmpty());
    }
}
