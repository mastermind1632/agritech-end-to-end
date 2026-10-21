package tut.ac.za.AgriFinanceAPIs.insights;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tut.ac.za.AgriFinanceAPIs.common.Roles;
import tut.ac.za.AgriFinanceAPIs.companion.BusinessProfile;
import tut.ac.za.AgriFinanceAPIs.companion.BusinessProfileRepository;
import tut.ac.za.AgriFinanceAPIs.companion.CompanionDtos;
import tut.ac.za.AgriFinanceAPIs.companion.CompanionService;
import tut.ac.za.AgriFinanceAPIs.exportready.ExportDtos;
import tut.ac.za.AgriFinanceAPIs.exportready.ExportService;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;
import tut.ac.za.AgriFinanceAPIs.finance.FinanceService;
import tut.ac.za.AgriFinanceAPIs.marketplace.DisputeDtos;
import tut.ac.za.AgriFinanceAPIs.marketplace.DisputeService;
import tut.ac.za.AgriFinanceAPIs.marketplace.ListingRepository;
import tut.ac.za.AgriFinanceAPIs.marketplace.MarketOrderRepository;
import tut.ac.za.AgriFinanceAPIs.statements.BankStatementRepository;
import tut.ac.za.AgriFinanceAPIs.statements.StatementDtos;
import tut.ac.za.AgriFinanceAPIs.statements.StatementService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Connects the five modules. A transparent rule-based segment decides which services each farmer is offered
 * (as the design guide recommends for day one), and "next steps" turn data from every module into actions.
 * The ML segmentation service (Floyd) refines buyer matching once enough marketplace data exists.
 */
@Service
public class InsightsService {

    public record Segment(String code, String name, String description, List<String> recommendedModules) { }

    public record NextStep(String id, String priority, String title, String text, String page) { }

    public record Overview(Segment segment, CompanionDtos.Summary companion, Map<String, Object> exports,
                           Map<String, Object> statements, Map<String, Object> marketplace, List<NextStep> nextSteps) { }

    private final FarmerRepository farmers;
    private final BusinessProfileRepository profiles;
    private final CompanionService companion;
    private final ExportService exports;
    private final StatementService statements;
    private final BankStatementRepository statementRepo;
    private final ListingRepository listings;
    private final MarketOrderRepository orders;
    private final DisputeService disputes;
    private final FinanceService finance;

    public InsightsService(FarmerRepository farmers, BusinessProfileRepository profiles, CompanionService companion,
                           ExportService exports, StatementService statements, BankStatementRepository statementRepo,
                           ListingRepository listings, MarketOrderRepository orders, DisputeService disputes,
                           FinanceService finance) {
        this.farmers = farmers;
        this.profiles = profiles;
        this.companion = companion;
        this.exports = exports;
        this.statements = statements;
        this.statementRepo = statementRepo;
        this.listings = listings;
        this.orders = orders;
        this.disputes = disputes;
        this.finance = finance;
    }

    /** Rule-based segmentation (replaceable by the ML model): maps a farm to the services it needs most. */
    public static Segment segment(BusinessProfile p, String role) {
        if (Roles.BUYER.equals(role)) return new Segment("BUYER", "Buyer", "Buys produce directly from farmers.", List.of("farmmarket"));
        if (Roles.SUPPLIER.equals(role)) return new Segment("SUPPLIER", "Input supplier", "Sells inputs to farmer groups.", List.of("supplier", "groups"));
        if (p == null || p.getIntakeCompletedAt() == null) {
            return new Segment("UNPROFILED", "New to the platform", "Answer the Business Builder questions so we can tailor the platform to your farm.",
                    List.of("companion", "farmmarket"));
        }
        boolean registered = !"NONE".equals(p.getLegalStructure()) && p.getRegistrationNumber() != null;
        BigDecimal ha = p.getHectares() == null ? BigDecimal.ZERO : p.getHectares();
        boolean bigTurnover = Set.of("1M_TO_5M", "OVER_5M").contains(p.getTurnoverBand());
        if (registered && p.isExportInterest() && (bigTurnover || ha.compareTo(BigDecimal.TEN) >= 0)) {
            return new Segment("EXPORT_CANDIDATE", "Commercial producer - export candidate",
                    "Registered, with the scale and interest to supply export markets.", List.of("export", "statements", "companion"));
        }
        if (registered) {
            return new Segment("REGISTERED_GROWER", "Registered commercial grower",
                    "Formal business; focus on costs, compliance and better prices.", List.of("statements", "companion", "farmmarket"));
        }
        if (ha.compareTo(new BigDecimal("5")) < 0) {
            return new Segment("EMERGING_SMALL", "Emerging grower, under 5 ha",
                    "Not yet formally registered. Formalising opens buyers, finance and grants.", List.of("companion", "farmmarket", "statements"));
        }
        return new Segment("EMERGING_GROWING", "Growing informal farm",
                "Enough scale to benefit from registration, a business account and a budget.", List.of("companion", "statements", "farmmarket"));
    }

    @Transactional(readOnly = true)
    public Overview overview(String userId) {
        Farmer f = farmers.findById(userId).orElseThrow();
        BusinessProfile p = profiles.findById(userId).orElse(null);
        Segment seg = segment(p, f.getRole());
        CompanionDtos.Summary cs = companion.summary(userId);

        List<ExportDtos.WorkflowSummary> plans = exports.list(userId);
        Map<String, Object> ex = new LinkedHashMap<>();
        ex.put("activePlans", plans.stream().filter(w -> "ACTIVE".equals(w.status())).count());
        ex.put("shipped", plans.stream().filter(w -> "SHIPPED".equals(w.status())).count());
        ex.put("bestReadiness", plans.stream().filter(w -> "ACTIVE".equals(w.status())).mapToInt(ExportDtos.WorkflowSummary::readiness).max().orElse(0));
        ex.put("exportReady", plans.stream().anyMatch(ExportDtos.WorkflowSummary::exportReady));

        List<StatementDtos.StatementView> sts = statements.list(userId);
        Map<String, Object> st = new LinkedHashMap<>();
        st.put("statements", sts.size());
        st.put("inReview", sts.stream().filter(s -> "REVIEW".equals(s.status())).count());
        st.put("needsReview", sts.stream().filter(s -> "REVIEW".equals(s.status())).mapToLong(StatementDtos.StatementView::needsReview).sum());
        st.put("latestPeriodEnd", sts.stream().filter(s -> s.periodEnd() != null).map(StatementDtos.StatementView::periodEnd).max(Comparator.naturalOrder()).orElse(null));

        Map<String, Object> mk = new LinkedHashMap<>();
        mk.put("activeListings", listings.countBySellerIdAndStatus(userId, "ACTIVE"));
        mk.put("ordersToAnswer", orders.countBySellerIdAndStatus(userId, "PENDING"));
        mk.put("completedSales", orders.countBySellerIdAndStatus(userId, "COMPLETED"));
        mk.put("buyerReliability", disputes.buyerReliability(userId));

        return new Overview(seg, cs, ex, st, mk, nextSteps(f, p, seg, cs, plans, sts, mk));
    }

    private List<NextStep> nextSteps(Farmer f, BusinessProfile p, Segment seg, CompanionDtos.Summary cs,
                                     List<ExportDtos.WorkflowSummary> plans, List<StatementDtos.StatementView> sts,
                                     Map<String, Object> mk) {
        List<NextStep> out = new ArrayList<>();
        String role = f.getRole();
        long toAnswer = (Long) mk.get("ordersToAnswer");
        if (toAnswer > 0) out.add(new NextStep("orders", "high", toAnswer + " order" + (toAnswer == 1 ? "" : "s") + " waiting for you",
                "Buyers are waiting. Accept or decline in Farm Market > Orders.", "farmmarket"));
        long disputesAgainst = disputes.mine(f.getId()).stream()
                .filter(d -> "AGAINST_ME".equals(d.myRole()) && "OPEN".equals(d.status())).count();
        if (disputesAgainst > 0) out.add(new NextStep("disputes", "high", "Respond to " + disputesAgainst + " dispute" + (disputesAgainst == 1 ? "" : "s"),
                "Give your side and upload evidence before the deadline.", "disputes"));
        if (cs.nextDeadline() != null && cs.nextDeadline().daysLeft() <= 14) {
            out.add(new NextStep("deadline", cs.nextDeadline().daysLeft() <= 3 ? "high" : "medium",
                    cs.nextDeadline().title(), "Due in " + cs.nextDeadline().daysLeft() + " days (" + cs.nextDeadline().dueDate() + ").", "companion"));
        }
        if (Roles.FARMER.equals(role) || Roles.ADMIN.equals(role)) {
            if (!cs.intakeCompleted()) {
                out.add(new NextStep("intake", "high", "Set up your Business Builder",
                        "Answer a few questions about your farm to get a personal checklist and compliance calendar.", "companion"));
            } else if (cs.percent() < 100) {
                out.add(new NextStep("checklist", "medium", "Business Builder: " + cs.percent() + "% done",
                        (cs.applicable() - cs.done()) + " steps left on your path to a fully formal farm business.", "companion"));
            }
            long review = sts.stream().filter(s -> "REVIEW".equals(s.status())).count();
            if (sts.isEmpty()) {
                out.add(new NextStep("statements", "medium", "Upload a bank statement",
                        "See where your money goes, your cost per crop and unusual expenses, in minutes.", "statements"));
            } else if (review > 0) {
                out.add(new NextStep("review", "medium", review + " statement" + (review == 1 ? "" : "s") + " to review",
                        "Check the categories and confirm to update your finances.", "statements"));
            }
            if ((Long) mk.get("activeListings") == 0) {
                out.add(new NextStep("listing", "low", "List your produce", "Buyers near you can find and order it on Farm Market.", "farmmarket"));
            }
            if ("EXPORT_CANDIDATE".equals(seg.code()) && plans.isEmpty()) {
                out.add(new NextStep("export", "medium", "You may be ready to export",
                        "Start an export-readiness checklist for your product and target market.", "export"));
            }
            List<FinanceService.MonthTotals> months = finance.monthly(f.getId(), 6);
            BigDecimal recent = BigDecimal.ZERO, earlier = BigDecimal.ZERO;
            for (int i = 0; i < months.size(); i++) {
                if (i >= 3) recent = recent.add(months.get(i).expenses()); else earlier = earlier.add(months.get(i).expenses());
            }
            if (earlier.signum() > 0) {
                double change = recent.subtract(earlier).divide(earlier, 4, RoundingMode.HALF_UP).doubleValue();
                if (change > 0.15) out.add(new NextStep("spending", "medium", "Spending is up " + Math.round(change * 100) + "%",
                        "Your last 3 months cost more than the 3 before. See which categories grew in Expense Analysis.", "statements"));
            }
        }
        if (p != null && p.getRegistrationNumber() != null && !p.isVerified()) {
            out.add(new NextStep("verify", "low", "Get the verified-business badge",
                    "Upload your CIPC certificate to the document vault so an administrator can verify your business.", "vault"));
        }
        out.sort(Comparator.comparingInt((NextStep n) -> switch (n.priority()) { case "high" -> 0; case "medium" -> 1; default -> 2; }));
        return out;
    }
}
