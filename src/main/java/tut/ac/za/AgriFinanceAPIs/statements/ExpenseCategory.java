package tut.ac.za.AgriFinanceAPIs.statements;

import java.util.Arrays;
import java.util.List;

/** The categories from the design guide (section 6.1). TRANSFER is money moved between own accounts. */
public enum ExpenseCategory {
    SEEDS("Seeds & seedlings", false),
    FERTILISER("Fertiliser", false),
    AGROCHEMICALS("Agrochemicals", false),
    FUEL("Fuel", false),
    LABOUR("Labour & wages", false),
    EQUIPMENT("Equipment purchase", false),
    REPAIRS("Repairs & maintenance", false),
    UTILITIES("Electricity & water", false),
    TRANSPORT("Transport & logistics", false),
    RENT("Rent & leases", false),
    LOAN("Loan repayments", false),
    INSURANCE("Insurance", false),
    BANK_FEES("Bank fees", false),
    FARM_SUPPLIES("Farm supplies (co-op)", false),
    PERSONAL("Personal / non-farm", false),
    OTHER_EXPENSE("Other farm expense", false),
    PRODUCE_SALES("Produce sales", true),
    GRANTS("Grants", true),
    OTHER_INCOME("Other income", true),
    TRANSFER("Transfer between own accounts", true);

    private final String label;
    private final boolean income;

    ExpenseCategory(String label, boolean income) {
        this.label = label;
        this.income = income;
    }

    public String label() { return label; }

    /** TRANSFER is listed with income only so it can be chosen for credits; it never counts as income. */
    public boolean isIncome() { return income && this != TRANSFER; }

    public boolean isFarmExpense() { return !income && this != PERSONAL; }

    public boolean countsInLedger() { return this != TRANSFER && this != PERSONAL; }

    public static ExpenseCategory parse(String code) {
        if (code == null) return null;
        try {
            return ExpenseCategory.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static List<ExpenseCategory> expenses() {
        return Arrays.stream(values()).filter(c -> !c.income).toList();
    }

    public static List<ExpenseCategory> incomes() {
        return Arrays.stream(values()).filter(c -> c.income).toList();
    }
}
