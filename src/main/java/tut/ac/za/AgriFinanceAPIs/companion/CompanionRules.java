package tut.ac.za.AgriFinanceAPIs.companion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * The companion's rules engine. Each guidance step in the content table carries a rule key; a step is added to a
 * farmer's checklist when its rule is true for their intake answers ("if the farm has employees, add UIF and COIDA").
 * The same answers generate the compliance calendar.
 *
 * Pure Java so every rule can be unit tested. Thresholds that change by law (such as the VAT threshold) are
 * passed in from configuration rather than hard-coded, and every generated deadline that is an estimate says so.
 */
public final class CompanionRules {

    /** Intake answers. Strings use the codes listed in the questionnaire. */
    public record Profile(String legalStructure, Integer employees, String turnoverBand, boolean vatRegistered,
                          boolean livestock, String waterSource, boolean irrigates, boolean usesAgrochemicals,
                          boolean sellsToRetailers, boolean exportInterest, boolean hasBusinessBankAccount,
                          LocalDate registeredOn, Integer financialYearEndMonth, String registrationNumber,
                          String taxNumber) { }

    public record DeadlineSpec(String code, String title, LocalDate dueDate, String authority, String sourceUrl,
                               boolean estimated) { }

    public static final List<String> STRUCTURES = List.of("NONE", "SOLE_PROPRIETOR", "PTY_LTD", "COOPERATIVE");
    public static final List<String> TURNOVER_BANDS = List.of("UNDER_50K", "50K_TO_1M", "1M_TO_5M", "OVER_5M");
    public static final List<String> WATER_SOURCES = List.of("RAINFED", "MUNICIPAL", "BOREHOLE", "RIVER", "DAM", "IRRIGATION_SCHEME");

    private final BigDecimal vatThreshold;

    public CompanionRules(BigDecimal vatThreshold) {
        this.vatThreshold = vatThreshold == null ? new BigDecimal("1000000") : vatThreshold;
    }

    /** Lower and upper bound of a turnover band in rand (upper is null for the open-ended band). */
    static BigDecimal[] band(String code) {
        if (code == null) return new BigDecimal[] { BigDecimal.ZERO, new BigDecimal("50000") };
        return switch (code) {
            case "50K_TO_1M" -> new BigDecimal[] { new BigDecimal("50000"), new BigDecimal("1000000") };
            case "1M_TO_5M" -> new BigDecimal[] { new BigDecimal("1000000"), new BigDecimal("5000000") };
            case "OVER_5M" -> new BigDecimal[] { new BigDecimal("5000000"), null };
            default -> new BigDecimal[] { BigDecimal.ZERO, new BigDecimal("50000") };
        };
    }

    public boolean applies(String rule, Profile p) {
        if (rule == null || rule.isBlank() || rule.equals("ALWAYS")) return true;
        if (rule.contains("|")) {
            for (String part : rule.split("\\|")) if (applies(part.trim(), p)) return true;
            return false;
        }
        String structure = p.legalStructure() == null ? "NONE" : p.legalStructure();
        int employees = p.employees() == null ? 0 : p.employees();
        BigDecimal[] band = band(p.turnoverBand());
        return switch (rule) {
            case "NO_STRUCTURE" -> structure.equals("NONE");
            case "HAS_STRUCTURE" -> !structure.equals("NONE");
            case "SOLE_PROPRIETOR" -> structure.equals("SOLE_PROPRIETOR");
            case "COMPANY" -> structure.equals("PTY_LTD");
            case "COOPERATIVE" -> structure.equals("COOPERATIVE");
            case "REGISTERED_ENTITY" -> structure.equals("PTY_LTD") || structure.equals("COOPERATIVE");
            case "HAS_EMPLOYEES" -> employees > 0;
            case "VAT_COMPULSORY" -> !p.vatRegistered() && band[0].compareTo(vatThreshold) >= 0;
            case "VAT_VOLUNTARY" -> !p.vatRegistered() && band[0].compareTo(new BigDecimal("50000")) >= 0
                    && band[0].compareTo(vatThreshold) < 0;
            case "VAT_REGISTERED" -> p.vatRegistered();
            case "LIVESTOCK" -> p.livestock();
            case "WATER_USE" -> p.irrigates() && p.waterSource() != null
                    && Set.of("BOREHOLE", "RIVER", "DAM").contains(p.waterSource());
            case "AGROCHEMICALS" -> p.usesAgrochemicals();
            case "RETAIL" -> p.sellsToRetailers();
            case "EXPORT" -> p.exportInterest();
            case "NO_BUSINESS_ACCOUNT" -> !p.hasBusinessBankAccount();
            default -> false;
        };
    }

    /** Compliance calendar entries for roughly the next 12 months. */
    public List<DeadlineSpec> deadlines(Profile p, LocalDate today) {
        List<DeadlineSpec> out = new ArrayList<>();
        String structure = p.legalStructure() == null ? "NONE" : p.legalStructure();
        int fye = p.financialYearEndMonth() == null || p.financialYearEndMonth() < 1 || p.financialYearEndMonth() > 12
                ? 2 : p.financialYearEndMonth();

        // CIPC annual return: due each year within 30 business days after the anniversary of incorporation.
        if ((structure.equals("PTY_LTD") || structure.equals("COOPERATIVE")) && p.registeredOn() != null) {
            LocalDate anniversary = p.registeredOn().withYear(today.getYear());
            if (anniversary.isBefore(today.minusDays(42))) anniversary = anniversary.plusYears(1);
            out.add(new DeadlineSpec("CIPC_ANNUAL_RETURN", "CIPC annual return (within 30 business days of your registration anniversary)",
                    addBusinessDays(anniversary, 30), "CIPC", "https://www.cipc.co.za", false));
        }

        // Provisional tax: payments at the 6-month point and at the end of the financial year.
        if (!structure.equals("NONE") || p.taxNumber() != null) {
            int firstMonth = ((fye + 6 - 1) % 12) + 1;
            YearMonth first = nextMonth(today, firstMonth);
            YearMonth second = nextMonth(today, fye);
            out.add(new DeadlineSpec("SARS_PROVISIONAL_" + first, "SARS provisional tax - first payment (IRP6)",
                    first.atEndOfMonth(), "SARS", "https://www.sars.gov.za", true));
            out.add(new DeadlineSpec("SARS_PROVISIONAL_" + second, "SARS provisional tax - second payment (IRP6)",
                    second.atEndOfMonth(), "SARS", "https://www.sars.gov.za", true));
        }

        if (p.vatRegistered()) {
            // Category A vendors file every two months; eFiling allows until the last business day of the next month.
            YearMonth m = YearMonth.from(today);
            int added = 0;
            for (int i = 0; added < 3 && i < 8; i++) {
                YearMonth period = m.plusMonths(i);
                if (period.getMonthValue() % 2 != 0) continue;
                LocalDate due = lastBusinessDay(period.plusMonths(1));
                if (due.isBefore(today)) continue;
                out.add(new DeadlineSpec("VAT201_" + period, "VAT201 return for the period ending " + period,
                        due, "SARS", "https://www.sars.gov.za", true));
                added++;
            }
        }

        int employees = p.employees() == null ? 0 : p.employees();
        if (employees > 0) {
            for (int i = 0; i < 3; i++) {
                YearMonth period = YearMonth.from(today).plusMonths(i - 1);
                LocalDate due = period.plusMonths(1).atDay(7);
                if (due.isBefore(today)) continue;
                out.add(new DeadlineSpec("EMP201_" + period, "EMP201 (PAYE, UIF, SDL) payment for " + period,
                        due, "SARS", "https://www.sars.gov.za", false));
                out.add(new DeadlineSpec("UIF_DECL_" + period, "UIF monthly declaration for " + period,
                        due, "Dept. of Employment and Labour", "https://www.ufiling.co.za", true));
            }
            LocalDate roe = LocalDate.of(today.getYear(), 5, 31);
            if (roe.isBefore(today)) roe = roe.plusYears(1);
            out.add(new DeadlineSpec("COIDA_ROE_" + roe.getYear(), "Compensation Fund Return of Earnings (COIDA)",
                    roe, "Compensation Fund", "https://www.labour.gov.za", true));
        }
        out.sort(Comparator.comparing(DeadlineSpec::dueDate));
        return out;
    }

    /** Progress score: completed applicable steps as a percentage. */
    public static int progress(int done, int applicable) {
        if (applicable <= 0) return 0;
        return (int) Math.round(100.0 * done / applicable);
    }

    private static YearMonth nextMonth(LocalDate today, int month) {
        YearMonth candidate = YearMonth.of(today.getYear(), month);
        return candidate.atEndOfMonth().isBefore(today) ? candidate.plusYears(1) : candidate;
    }

    static LocalDate addBusinessDays(LocalDate d, int days) {
        LocalDate x = d;
        int added = 0;
        while (added < days) {
            x = x.plusDays(1);
            if (x.getDayOfWeek().getValue() <= 5) added++;
        }
        return x;
    }

    static LocalDate lastBusinessDay(YearMonth m) {
        LocalDate d = m.atEndOfMonth();
        while (d.getDayOfWeek().getValue() > 5) d = d.minusDays(1);
        return d;
    }
}
