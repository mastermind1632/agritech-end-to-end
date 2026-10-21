package tut.ac.za.AgriFinanceAPIs.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tut.ac.za.AgriFinanceAPIs.common.Roles;
import tut.ac.za.AgriFinanceAPIs.companion.CompanionDtos;
import tut.ac.za.AgriFinanceAPIs.companion.CompanionService;
import tut.ac.za.AgriFinanceAPIs.exportready.ExportDtos;
import tut.ac.za.AgriFinanceAPIs.exportready.ExportService;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;
import tut.ac.za.AgriFinanceAPIs.finance.Expense;
import tut.ac.za.AgriFinanceAPIs.finance.ExpenseRepository;
import tut.ac.za.AgriFinanceAPIs.finance.Income;
import tut.ac.za.AgriFinanceAPIs.finance.IncomeRepository;
import tut.ac.za.AgriFinanceAPIs.grouporder.GroupOrder;
import tut.ac.za.AgriFinanceAPIs.grouporder.GroupOrderRepository;
import tut.ac.za.AgriFinanceAPIs.marketplace.Listing;
import tut.ac.za.AgriFinanceAPIs.marketplace.ListingRepository;
import tut.ac.za.AgriFinanceAPIs.privacy.PrivacyService;
import tut.ac.za.AgriFinanceAPIs.supplier.Supplier;
import tut.ac.za.AgriFinanceAPIs.supplier.SupplierProduct;
import tut.ac.za.AgriFinanceAPIs.supplier.SupplierProductRepository;
import tut.ac.za.AgriFinanceAPIs.supplier.SupplierRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Demo data for presentations (APP_DEMO_SEED=true). Creates a few clearly fictional accounts with listings,
 * finances, a business profile, crop plans, an export plan and a supplier with a group order. Runs once: it is
 * skipped when the demo farmer already exists. Never enable it on a production database with real users.
 *
 * Accounts (password Demo@2026 for all): 0710000001 farmer, 0710000002 emerging farmer, 0720000001 buyer,
 * 0730000001 supplier, 0700000000 admin.
 */
@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String PASSWORD = "Demo@2026";

    private final boolean enabled;
    private final FarmerRepository farmers;
    private final PasswordEncoder encoder;
    private final ListingRepository listings;
    private final ExpenseRepository expenses;
    private final IncomeRepository incomes;
    private final SupplierRepository suppliers;
    private final SupplierProductRepository products;
    private final GroupOrderRepository groupOrders;
    private final CompanionService companion;
    private final ExportService exports;
    private final PrivacyService privacy;

    public DemoDataSeeder(@Value("${app.demo.seed:false}") boolean enabled, FarmerRepository farmers, PasswordEncoder encoder,
                          ListingRepository listings, ExpenseRepository expenses, IncomeRepository incomes,
                          SupplierRepository suppliers, SupplierProductRepository products, GroupOrderRepository groupOrders,
                          CompanionService companion, ExportService exports, PrivacyService privacy) {
        this.enabled = enabled;
        this.farmers = farmers;
        this.encoder = encoder;
        this.listings = listings;
        this.expenses = expenses;
        this.incomes = incomes;
        this.suppliers = suppliers;
        this.products = products;
        this.groupOrders = groupOrders;
        this.companion = companion;
        this.exports = exports;
        this.privacy = privacy;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || farmers.findByContact("0710000001").isPresent()) return;
        log.warn("APP_DEMO_SEED=true: creating demo accounts (password {}). Do not use on a production database.", PASSWORD);

        Farmer admin = farmer("0700000000", "Demo Administrator", "Pretoria", "admin@demo.agritech", Roles.ADMIN);
        Farmer thandi = farmer("0710000001", "Thandi Maluleke (demo)", "Tzaneen, Limpopo", "thandi@demo.agritech", Roles.FARMER);
        Farmer sipho = farmer("0710000002", "Sipho Ndlovu (demo)", "Polokwane, Limpopo", null, Roles.FARMER);
        farmer("0720000001", "Fresh Grocer Polokwane (demo)", "Polokwane, Limpopo", "buyer@demo.agritech", Roles.BUYER);
        Farmer supplierUser = farmer("0730000001", "Limpopo Agri Supplies (demo)", "Polokwane, Limpopo", "supplier@demo.agritech", Roles.SUPPLIER);

        // ---- Thandi: a registered grower interested in exports
        companion.saveProfile(thandi.getId(), new CompanionDtos.ProfileRequest("PTY_LTD", "Maluleke Fresh Produce (Pty) Ltd (demo)",
                "2021/555555/07", LocalDate.of(2021, 3, 15), "9000000000", false, null, 4, "1M_TO_5M",
                "Tomatoes, Butternut, Avocados", new BigDecimal("12"), false, "BOREHOLE", true, true, true, true, true, 2, "Limpopo"));
        companion.verifyBusiness(thandi.getId(), true, admin.getId(), "Demo verification");
        for (String code : List.of("SARS_REGISTER", "BUSINESS_ACCOUNT", "UIF_REGISTER", "RECORDS_KEEP", "CIPC_REGISTER")) {
            try {
                companion.updateTask(thandi.getId(), code, new CompanionDtos.TaskUpdate("DONE", "Demo data", null));
            } catch (RuntimeException ignored) { }
        }
        companion.saveCropPlan(thandi.getId(), null, new CompanionDtos.CropPlanRequest("Tomatoes", new BigDecimal("3"),
                new BigDecimal("45000"), "kg", new BigDecimal("9.50"), new BigDecimal("85000"), 9, 1));
        companion.saveCropPlan(thandi.getId(), null, new CompanionDtos.CropPlanRequest("Butternut", new BigDecimal("4"),
                new BigDecimal("18000"), "kg", new BigDecimal("6.00"), new BigDecimal("38000"), 10, 2));
        companion.saveCropPlan(thandi.getId(), null, new CompanionDtos.CropPlanRequest("Avocados", new BigDecimal("5"),
                new BigDecimal("9000"), "kg", new BigDecimal("22.00"), new BigDecimal("60000"), 7, 5));
        exports.start(thandi.getId(), new ExportDtos.StartWorkflow("AVOCADO", "EU", LocalDate.now().plusMonths(7),
                new BigDecimal("20"), "tonne"));

        listing(thandi, "Tomatoes", "Vegetables", "Class 1", "crate", "120.00", "80", "Tzaneen, Limpopo", -23.83, 30.16);
        listing(thandi, "Butternut", "Vegetables", "Class 1", "kg", "7.50", "1500", "Tzaneen, Limpopo", -23.83, 30.16);
        listing(thandi, "Avocados (Hass)", "Fruit", "Export grade", "kg", "28.00", "900", "Tzaneen, Limpopo", -23.83, 30.16);

        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(5);
        String[][] costs = {
                { "Engen Tzaneen diesel", "Fuel", "3200" }, { "Omnia fertiliser", "Fertiliser", "6800" },
                { "Wages - farm workers", "Labour & wages", "14000" }, { "Eskom electricity (borehole pump)", "Electricity & water", "2100" },
                { "Starke Ayres seedlings", "Seeds & seedlings", "4500" }, { "Agrochemicals - fungicide", "Agrochemicals", "3900" } };
        for (int m = 0; m < 6; m++) {
            LocalDate d = start.plusMonths(m);
            for (String[] c : costs) {
                BigDecimal amount = new BigDecimal(c[2]).multiply(BigDecimal.valueOf(0.85 + 0.06 * m)).setScale(2, java.math.RoundingMode.HALF_UP);
                expense(thandi, c[0], c[1], amount, d.plusDays(4 + m), c[1].equals("Seeds & seedlings") ? "Tomatoes" : null);
            }
            income(thandi, "Fresh produce sales - Joburg Market", new BigDecimal(m >= 3 ? "68000" : "31000"), d.plusDays(20));
        }

        // ---- Sipho: an emerging grower (no business registration yet)
        listing(sipho, "Spinach", "Vegetables", null, "bunch", "12.00", "300", "Polokwane, Limpopo", -23.90, 29.45);
        listing(sipho, "Green peppers", "Vegetables", null, "kg", "18.00", "250", "Polokwane, Limpopo", -23.90, 29.45);
        expense(sipho, "Seed and seedlings", "Seeds & seedlings", new BigDecimal("1800"), LocalDate.now().minusDays(40), null);
        income(sipho, "Sales at taxi rank stall", new BigDecimal("4200"), LocalDate.now().minusDays(10));

        // ---- Supplier catalogue with an open group order
        Supplier s = new Supplier();
        s.setName("Limpopo Agri Supplies (demo)");
        s.setLocation("Polokwane");
        s.setContact("0730000001");
        s.setOwnerId(supplierUser.getId());
        suppliers.save(s);
        SupplierProduct fert = product(s, "LAN 28 fertiliser 50 kg", "520.00");
        product(s, "Butternut seed 500 g (Waltham)", "690.00");
        product(s, "Drip irrigation tape 1 000 m", "1450.00");
        GroupOrder g = new GroupOrder();
        g.setProductId(fert.getId());
        g.setTargetQuantity(100);
        g.setDiscountRate(new BigDecimal("12"));
        g.setCreatedBy(supplierUser.getId());
        g.setDeadline(LocalDateTime.now().plusDays(21));
        groupOrders.save(g);
        log.info("Demo data ready. Sign in with 0710000001 / {}", PASSWORD);
    }

    private Farmer farmer(String contact, String name, String location, String email, String role) {
        Farmer f = new Farmer();
        f.setContact(contact);
        f.setName(name);
        f.setLocation(location);
        f.setEmail(email);
        f.setRole(role);
        f.setPasswordHash(encoder.encode(PASSWORD));
        Farmer saved = farmers.save(f);
        privacy.record(saved.getId(), "TERMS", true);
        privacy.record(saved.getId(), "PRIVACY", true);
        return saved;
    }

    private void listing(Farmer seller, String product, String category, String grade, String unit, String price, String qty,
                         String location, double lat, double lng) {
        Listing l = new Listing();
        l.setSellerId(seller.getId());
        l.setProduct(product);
        l.setCategory(category);
        l.setGrade(grade);
        l.setUnit(unit);
        l.setPricePerUnit(new BigDecimal(price));
        l.setQuantityAvailable(new BigDecimal(qty));
        l.setLocation(location);
        l.setLatitude(lat);
        l.setLongitude(lng);
        l.setDescription("Demo listing - freshly harvested and available for collection or local delivery.");
        l.setAvailableFrom(LocalDate.now());
        l.setAvailableUntil(LocalDate.now().plusMonths(2));
        listings.save(l);
    }

    private void expense(Farmer f, String item, String category, BigDecimal amount, LocalDate date, String crop) {
        Expense e = new Expense();
        e.setFarmerId(f.getId());
        e.setItem(item);
        e.setCategory(category);
        e.setAmount(amount);
        e.setDate(date);
        e.setCrop(crop);
        expenses.save(e);
    }

    private void income(Farmer f, String item, BigDecimal amount, LocalDate date) {
        Income i = new Income();
        i.setFarmerId(f.getId());
        i.setItem(item);
        i.setAmount(amount);
        i.setDate(date);
        incomes.save(i);
    }

    private SupplierProduct product(Supplier s, String name, String price) {
        SupplierProduct p = new SupplierProduct();
        p.setSupplierId(s.getId());
        p.setProductName(name);
        p.setPrice(new BigDecimal(price));
        return products.save(p);
    }
}
