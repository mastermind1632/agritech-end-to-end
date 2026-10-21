package tut.ac.za.AgriFinanceAPIs.companion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;
import tut.ac.za.AgriFinanceAPIs.companion.CompanionDtos.*;
import tut.ac.za.AgriFinanceAPIs.finance.ExpenseRepository;
import tut.ac.za.AgriFinanceAPIs.marketplace.ListingRepository;
import tut.ac.za.AgriFinanceAPIs.marketplace.MarketOrderRepository;
import tut.ac.za.AgriFinanceAPIs.notification.NotificationHub;
import tut.ac.za.AgriFinanceAPIs.reports.SimpleXlsxWriter;
import tut.ac.za.AgriFinanceAPIs.vault.VaultDocumentInfo;
import tut.ac.za.AgriFinanceAPIs.vault.VaultDocumentRepository;
import tut.ac.za.AgriFinanceAPIs.vault.VaultService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module 3: the farm business-building companion. A guided path: intake questions, a personalised checklist
 * produced by the rules engine, a compliance calendar with reminders, and a budget / cash-flow plan pre-filled
 * from the marketplace (prices) and the expense module (actual costs).
 */
@Service
public class CompanionService {

    public static final String DISCLAIMER = "This companion gives guidance and organisation, not tax or legal advice. "
            + "Where a decision has financial consequences (company structure, VAT), speak to a registered accountant. "
            + "Requirements change: always confirm with the official source linked on each step.";
    static final Set<String> TASK_STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE", "NOT_APPLICABLE");
    static final Map<String, List<String>> OPTIONS = new LinkedHashMap<>();
    static {
        OPTIONS.put("legalStructure", CompanionRules.STRUCTURES);
        OPTIONS.put("turnoverBand", CompanionRules.TURNOVER_BANDS);
        OPTIONS.put("waterSource", CompanionRules.WATER_SOURCES);
        OPTIONS.put("province", List.of("Eastern Cape", "Free State", "Gauteng", "KwaZulu-Natal", "Limpopo", "Mpumalanga",
                "North West", "Northern Cape", "Western Cape"));
    }

    private final BusinessProfileRepository profiles;
    private final CompanionStepRepository steps;
    private final CompanionTaskRepository tasks;
    private final ComplianceDeadlineRepository deadlines;
    private final CropPlanRepository plans;
    private final ListingRepository listings;
    private final MarketOrderRepository orders;
    private final ExpenseRepository expenses;
    private final VaultService vault;
    private final VaultDocumentRepository documents;
    private final NotificationHub notifications;
    private final AuditService audit;
    private final CompanionRules rules;

    public CompanionService(BusinessProfileRepository profiles, CompanionStepRepository steps, CompanionTaskRepository tasks,
                            ComplianceDeadlineRepository deadlines, CropPlanRepository plans, ListingRepository listings,
                            MarketOrderRepository orders, ExpenseRepository expenses, VaultService vault,
                            VaultDocumentRepository documents, NotificationHub notifications, AuditService audit,
                            @Value("${app.companion.vat-threshold:1000000}") BigDecimal vatThreshold) {
        this.profiles = profiles;
        this.steps = steps;
        this.tasks = tasks;
        this.deadlines = deadlines;
        this.plans = plans;
        this.listings = listings;
        this.orders = orders;
        this.expenses = expenses;
        this.vault = vault;
        this.documents = documents;
        this.notifications = notifications;
        this.audit = audit;
        this.rules = new CompanionRules(vatThreshold);
    }

    // ------------------------------------------------------------------ profile (intake questionnaire)

    @Transactional(readOnly = true)
    public ProfileView profile(String farmerId) {
        BusinessProfile p = profiles.findById(farmerId).orElseGet(() -> blank(farmerId));
        return view(p);
    }

    @Transactional
    public ProfileView saveProfile(String farmerId, ProfileRequest r) {
        if (r == null) throw bad("Answer the questions first");
        BusinessProfile p = profiles.findById(farmerId).orElseGet(() -> blank(farmerId));
        String structure = upper(r.legalStructure(), "NONE");
        if (!CompanionRules.STRUCTURES.contains(structure)) throw bad("Unknown business structure");
        String band = upper(r.turnoverBand(), "UNDER_50K");
        if (!CompanionRules.TURNOVER_BANDS.contains(band)) throw bad("Unknown turnover band");
        String water = r.waterSource() == null || r.waterSource().isBlank() ? null : r.waterSource().trim().toUpperCase();
        if (water != null && !CompanionRules.WATER_SOURCES.contains(water)) throw bad("Unknown water source");
        int employees = r.employees() == null ? 0 : r.employees();
        if (employees < 0 || employees > 10000) throw bad("Employees must be between 0 and 10 000");
        if (r.hectares() != null && (r.hectares().signum() < 0 || r.hectares().compareTo(new BigDecimal("1000000")) > 0)) {
            throw bad("Hectares must be a positive number");
        }
        int fye = r.financialYearEndMonth() == null ? 2 : r.financialYearEndMonth();
        if (fye < 1 || fye > 12) throw bad("Financial year end must be a month from 1 to 12");
        if (r.registeredOn() != null && r.registeredOn().isAfter(LocalDate.now())) throw bad("Registration date cannot be in the future");

        String oldReg = p.getRegistrationNumber();
        p.setLegalStructure(structure);
        p.setBusinessName(clip(r.businessName(), 150));
        p.setRegistrationNumber(clip(r.registrationNumber(), 40));
        p.setRegisteredOn(r.registeredOn());
        p.setTaxNumber(clip(r.taxNumber(), 20));
        p.setVatRegistered(Boolean.TRUE.equals(r.vatRegistered()));
        p.setVatNumber(p.isVatRegistered() ? clip(r.vatNumber(), 20) : null);
        p.setEmployees(employees);
        p.setTurnoverBand(band);
        p.setCrops(clip(r.crops(), 300));
        p.setHectares(r.hectares());
        p.setLivestock(Boolean.TRUE.equals(r.livestock()));
        p.setWaterSource(water);
        p.setIrrigates(Boolean.TRUE.equals(r.irrigates()));
        p.setUsesAgrochemicals(Boolean.TRUE.equals(r.usesAgrochemicals()));
        p.setSellsToRetailers(Boolean.TRUE.equals(r.sellsToRetailers()));
        p.setExportInterest(Boolean.TRUE.equals(r.exportInterest()));
        p.setHasBusinessAccount(Boolean.TRUE.equals(r.hasBusinessAccount()));
        p.setFinancialYearEndMonth(fye);
        p.setProvince(clip(r.province(), 40));
        if (p.getIntakeCompletedAt() == null) p.setIntakeCompletedAt(LocalDateTime.now());
        boolean regChanged = !Objects.equals(oldReg, p.getRegistrationNumber());
        if (regChanged) {
            p.setVerified(false);
            p.setVerifiedAt(null);
            p.setVerifiedBy(null);
        }
        profiles.save(p);
        regenerateDeadlines(farmerId, p);
        if (regChanged && p.getRegistrationNumber() != null) {
            notifications.notifyAdmins("ACCOUNT", "Business verification requested",
                    (p.getBusinessName() == null ? "A farm" : p.getBusinessName()) + " added registration number " + p.getRegistrationNumber() + ".", "admin");
        }
        audit.record(farmerId, "BUSINESS_PROFILE_SAVED", "BUSINESS_PROFILE", farmerId, structure + ", " + employees + " employees");
        return view(p);
    }

    // ------------------------------------------------------------------ checklist (rules engine)

    @Transactional(readOnly = true)
    public Checklist checklist(String farmerId) {
        BusinessProfile p = profiles.findById(farmerId).orElseGet(() -> blank(farmerId));
        CompanionRules.Profile rp = p.toRulesProfile();
        Map<String, CompanionTask> byCode = tasks.findByFarmerId(farmerId).stream()
                .collect(Collectors.toMap(CompanionTask::getStepCode, t -> t, (a, b) -> a));
        Set<String> docIds = byCode.values().stream().map(CompanionTask::getDocumentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> docTitles = docIds.isEmpty() ? Map.of() : documents.infos(docIds).stream()
                .filter(d -> d.ownerId().equals(farmerId))
                .collect(Collectors.toMap(VaultDocumentInfo::id, VaultDocumentInfo::title));

        Map<Integer, List<StepView>> stages = new TreeMap<>();
        Map<Integer, String> stageNames = new HashMap<>();
        int done = 0, applicable = 0;
        for (CompanionStep s : steps.findByActiveTrueOrderByStageAscSortOrderAsc()) {
            if (!rules.applies(s.getRuleKey(), rp)) continue;
            CompanionTask t = byCode.get(s.getCode());
            String status = t == null ? "TODO" : t.getStatus();
            if (!"NOT_APPLICABLE".equals(status)) {
                applicable++;
                if ("DONE".equals(status)) done++;
            }
            stageNames.put(s.getStage(), s.getStageName());
            String docId = t == null ? null : t.getDocumentId();
            stages.computeIfAbsent(s.getStage(), k -> new ArrayList<>()).add(new StepView(s.getCode(), s.getStage(),
                    s.getStageName(), s.getTitle(), s.getDescription(), s.getWhy(), s.getAuthority(), s.getLinkUrl(),
                    s.getLinkLabel(), s.getModuleLink(), status, t == null ? null : t.getNote(), docId,
                    docId == null ? null : docTitles.get(docId), t == null ? null : t.getCompletedAt(),
                    s.getReviewStatus(), s.getLastVerified()));
        }
        List<StageView> stageViews = new ArrayList<>();
        for (Map.Entry<Integer, List<StepView>> e : stages.entrySet()) {
            int d = 0, a = 0;
            for (StepView sv : e.getValue()) {
                if ("NOT_APPLICABLE".equals(sv.status())) continue;
                a++;
                if ("DONE".equals(sv.status())) d++;
            }
            stageViews.add(new StageView(e.getKey(), stageNames.get(e.getKey()), d, a, CompanionRules.progress(d, a), e.getValue()));
        }
        return new Checklist(CompanionRules.progress(done, applicable), done, applicable, p.getIntakeCompletedAt() != null,
                stageViews, DISCLAIMER);
    }

    @Transactional
    public Checklist updateTask(String farmerId, String code, TaskUpdate u) {
        CompanionStep step = steps.findById(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found"));
        String status = u == null || u.status() == null ? "TODO" : u.status().trim().toUpperCase();
        if (!TASK_STATUSES.contains(status)) throw bad("Status must be TODO, IN_PROGRESS, DONE or NOT_APPLICABLE");
        CompanionTask t = tasks.findByFarmerIdAndStepCode(farmerId, code).orElseGet(() -> {
            CompanionTask n = new CompanionTask();
            n.setFarmerId(farmerId);
            n.setStepCode(code);
            return n;
        });
        if (u != null && u.documentId() != null && !u.documentId().isBlank()) {
            vault.requireOwned(u.documentId(), farmerId);
            t.setDocumentId(u.documentId());
        } else if (u != null && u.documentId() != null) {
            t.setDocumentId(null);
        }
        t.setStatus(status);
        if (u != null && u.note() != null) t.setNote(clip(u.note(), 500));
        t.setCompletedAt("DONE".equals(status) ? (t.getCompletedAt() == null ? LocalDateTime.now() : t.getCompletedAt()) : null);
        tasks.save(t);
        audit.record(farmerId, "COMPANION_STEP_" + status, "COMPANION_STEP", code, step.getTitle());
        return checklist(farmerId);
    }

    // ------------------------------------------------------------------ compliance calendar

    @Transactional
    public void regenerateDeadlines(String farmerId, BusinessProfile p) {
        LocalDate today = LocalDate.now();
        deadlines.deleteByFarmerIdAndCustomFalseAndStatusAndDueDateGreaterThanEqual(farmerId, "UPCOMING", today);
        deadlines.flush();
        for (CompanionRules.DeadlineSpec spec : rules.deadlines(p.toRulesProfile(), today)) {
            if (deadlines.existsByFarmerIdAndCodeAndDueDate(farmerId, spec.code(), spec.dueDate())) continue;
            ComplianceDeadline d = new ComplianceDeadline();
            d.setFarmerId(farmerId);
            d.setCode(spec.code());
            d.setTitle(spec.title());
            d.setDueDate(spec.dueDate());
            d.setAuthority(spec.authority());
            d.setSourceUrl(spec.sourceUrl());
            d.setEstimated(spec.estimated());
            d.setCustom(false);
            deadlines.save(d);
        }
    }

    @Transactional(readOnly = true)
    public List<DeadlineView> deadlines(String farmerId) {
        LocalDate today = LocalDate.now();
        List<DeadlineView> out = new ArrayList<>();
        for (ComplianceDeadline d : deadlines.findByFarmerIdOrderByDueDateAsc(farmerId)) {
            out.add(new DeadlineView(d.getId(), d.getCode(), d.getTitle(), d.getDueDate(), ChronoUnit.DAYS.between(today, d.getDueDate()),
                    d.getAuthority(), d.getSourceUrl(), d.isEstimated(), d.isCustom(), d.getStatus(), d.getDocumentId(),
                    d.isCustom() ? "CUSTOM" : "COMPLIANCE"));
        }
        // Expiry dates of documents in the vault (permits, certificates) appear in the same calendar.
        for (VaultDocumentInfo doc : vault.list(farmerId)) {
            if (doc.expiresOn() == null || doc.expiresOn().isBefore(today.minusDays(30))) continue;
            out.add(new DeadlineView(null, "DOCUMENT_EXPIRY", doc.title() + " expires", doc.expiresOn(),
                    ChronoUnit.DAYS.between(today, doc.expiresOn()), "Document vault", null, false, false,
                    doc.expiresOn().isBefore(today) ? "MISSED" : "UPCOMING", doc.id(), "DOCUMENT"));
        }
        out.sort(Comparator.comparing(DeadlineView::dueDate));
        return out;
    }

    @Transactional
    public DeadlineView addDeadline(String farmerId, DeadlineRequest r) {
        if (r == null || r.title() == null || r.title().isBlank()) throw bad("Give the deadline a title");
        if (r.dueDate() == null) throw bad("Choose a due date");
        if (r.documentId() != null && !r.documentId().isBlank()) vault.requireOwned(r.documentId(), farmerId);
        ComplianceDeadline d = new ComplianceDeadline();
        d.setFarmerId(farmerId);
        d.setCode("CUSTOM_" + UUID.randomUUID().toString().substring(0, 8));
        d.setTitle(clip(r.title().trim(), 200));
        d.setDueDate(r.dueDate());
        d.setAuthority(clip(r.authority(), 80));
        d.setCustom(true);
        d.setDocumentId(r.documentId() == null || r.documentId().isBlank() ? null : r.documentId());
        deadlines.save(d);
        return deadlines(farmerId).stream().filter(x -> d.getId().equals(x.id())).findFirst().orElseThrow();
    }

    @Transactional
    public List<DeadlineView> setDeadlineStatus(String farmerId, Long id, String status) {
        ComplianceDeadline d = ownedDeadline(farmerId, id);
        String s = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("UPCOMING", "DONE").contains(s)) throw bad("Status must be UPCOMING or DONE");
        d.setStatus(s);
        deadlines.save(d);
        audit.record(farmerId, "DEADLINE_" + s, "DEADLINE", String.valueOf(id), d.getTitle());
        return deadlines(farmerId);
    }

    @Transactional
    public void deleteDeadline(String farmerId, Long id) {
        ComplianceDeadline d = ownedDeadline(farmerId, id);
        if (!d.isCustom()) throw bad("Generated deadlines cannot be deleted; mark them as done instead");
        deadlines.delete(d);
    }

    private ComplianceDeadline ownedDeadline(String farmerId, Long id) {
        ComplianceDeadline d = deadlines.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deadline not found"));
        if (!d.getFarmerId().equals(farmerId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Deadline not found");
        return d;
    }

    // ------------------------------------------------------------------ business plan, budget and cash flow

    @Transactional(readOnly = true)
    public PlanView plan(String farmerId, BigDecimal openingCash) {
        List<CropPlanView> crops = plans.findByFarmerIdOrderByCropAsc(farmerId).stream().map(this::planView).toList();
        List<String> assumptions = new ArrayList<>();
        List<CashflowProjector.CropInput> inputs = new ArrayList<>();
        for (CropPlanView c : crops) {
            BigDecimal price = c.expectedPrice() != null ? c.expectedPrice() : c.marketPrice();
            if (c.expectedPrice() == null && c.marketPrice() != null) {
                assumptions.add(c.crop() + ": price R" + c.marketPrice() + " per " + c.marketPriceUnit() + " taken from current Farm Market prices.");
            }
            BigDecimal cost = c.costPerHa() != null ? c.costPerHa() : c.actualCostPerHa();
            if (c.costPerHa() == null && c.actualCostPerHa() != null) {
                assumptions.add(c.crop() + ": cost R" + c.actualCostPerHa() + " per hectare taken from your recorded expenses for this crop.");
            }
            if (price == null) assumptions.add(c.crop() + ": no price yet - add an expected price to include its income.");
            if (cost == null) assumptions.add(c.crop() + ": no cost per hectare yet - add one or tag expenses with this crop.");
            inputs.add(new CashflowProjector.CropInput(c.crop(), c.hectares(), c.yieldPerHa(), c.yieldUnit(), price, cost,
                    c.plantMonth(), c.harvestMonth()));
        }
        BigDecimal untagged = expenses.sumUntaggedSince(farmerId, LocalDate.now().minusMonths(6));
        BigDecimal overheads = untagged == null ? BigDecimal.ZERO : untagged.divide(BigDecimal.valueOf(6), 2, RoundingMode.HALF_UP);
        assumptions.add("Monthly overheads of R" + overheads + " are the average of your untagged expenses over the last 6 months.");
        BigDecimal opening = openingCash == null ? BigDecimal.ZERO : openingCash;
        CashflowProjector.Projection projection = CashflowProjector.project(inputs, overheads, opening, YearMonth.now());
        if (projection.lowestCumulative() != null && projection.lowestCumulative().signum() < 0) {
            assumptions.add("Cash is lowest in " + projection.lowestMonth() + " at R" + projection.lowestCumulative()
                    + ": that is the working capital or credit you need to reach harvest.");
        }
        return new PlanView(crops, projection, overheads, opening, assumptions);
    }

    @Transactional
    public CropPlanView saveCropPlan(String farmerId, Long id, CropPlanRequest r) {
        if (r == null || r.crop() == null || r.crop().isBlank()) throw bad("Crop name is required");
        if (r.hectares() == null || r.hectares().signum() <= 0) throw bad("Hectares must be greater than zero");
        if (r.yieldPerHa() == null || r.yieldPerHa().signum() <= 0) throw bad("Expected yield per hectare must be greater than zero");
        if (r.yieldUnit() == null || r.yieldUnit().isBlank()) throw bad("Yield unit is required (e.g. kg, tonne, crate)");
        int plant = r.plantMonth() == null ? 0 : r.plantMonth();
        int harvest = r.harvestMonth() == null ? 0 : r.harvestMonth();
        if (plant < 1 || plant > 12 || harvest < 1 || harvest > 12) throw bad("Planting and harvest months must be 1 to 12");
        if (r.expectedPrice() != null && r.expectedPrice().signum() < 0) throw bad("Price cannot be negative");
        if (r.costPerHa() != null && r.costPerHa().signum() < 0) throw bad("Cost cannot be negative");
        CropPlan p;
        if (id == null) {
            p = new CropPlan();
            p.setFarmerId(farmerId);
        } else {
            p = plans.findById(id).filter(x -> x.getFarmerId().equals(farmerId))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crop plan not found"));
        }
        p.setCrop(clip(r.crop().trim(), 60));
        p.setHectares(r.hectares());
        p.setYieldPerHa(r.yieldPerHa());
        p.setYieldUnit(clip(r.yieldUnit().trim().toLowerCase(), 20));
        p.setExpectedPrice(r.expectedPrice());
        p.setCostPerHa(r.costPerHa());
        p.setPlantMonth(plant);
        p.setHarvestMonth(harvest);
        return planView(plans.save(p));
    }

    @Transactional
    public void deleteCropPlan(String farmerId, Long id) {
        CropPlan p = plans.findById(id).filter(x -> x.getFarmerId().equals(farmerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Crop plan not found"));
        plans.delete(p);
    }

    /** Hectares per planned crop, used by the expense module to split shared costs. */
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> hectaresByCrop(String farmerId) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (CropPlan p : plans.findByFarmerIdOrderByCropAsc(farmerId)) out.merge(p.getCrop(), p.getHectares(), BigDecimal::add);
        return out;
    }

    public byte[] planWorkbook(String farmerId, BigDecimal openingCash) {
        PlanView plan = plan(farmerId, openingCash);
        List<List<Object>> budget = new ArrayList<>();
        budget.add(List.of("Crop", "Hectares", "Expected volume", "Unit", "Revenue (R)", "Cost (R)", "Margin (R)", "Margin per ha (R)", "Break-even price (R/unit)"));
        for (CashflowProjector.CropBudget b : plan.projection().crops()) {
            budget.add(Arrays.asList(b.crop(), b.hectares(), b.expectedVolume(), b.unit(), b.revenue(), b.cost(), b.margin(),
                    b.marginPerHa(), b.breakEvenPrice()));
        }
        List<List<Object>> flow = new ArrayList<>();
        flow.add(List.of("Month", "Income (R)", "Crop costs (R)", "Overheads (R)", "Net (R)", "Cumulative cash (R)"));
        for (CashflowProjector.MonthFlow m : plan.projection().months()) {
            flow.add(List.of(m.month(), m.income(), m.cropCosts(), m.overheads(), m.net(), m.cumulative()));
        }
        List<List<Object>> notes = new ArrayList<>();
        notes.add(List.of("Assumptions"));
        plan.assumptions().forEach(a -> notes.add(List.of(a)));
        notes.add(List.of(DISCLAIMER));
        return new SimpleXlsxWriter().sheet("Budget", budget).sheet("Cash flow", flow).sheet("Assumptions", notes).toBytes();
    }

    // ------------------------------------------------------------------ admin: content and verification

    public List<CompanionStep> allSteps() {
        return steps.findAllByOrderByStageAscSortOrderAsc();
    }

    @Transactional
    public CompanionStep editStep(String code, StepEdit e, String adminId) {
        CompanionStep s = steps.findById(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found"));
        if (e.title() != null && !e.title().isBlank()) s.setTitle(clip(e.title().trim(), 150));
        if (e.description() != null && !e.description().isBlank()) s.setDescription(clip(e.description().trim(), 2000));
        if (e.why() != null) s.setWhy(clip(e.why(), 1000));
        if (e.authority() != null) s.setAuthority(clip(e.authority(), 80));
        if (e.ruleKey() != null && !e.ruleKey().isBlank()) s.setRuleKey(clip(e.ruleKey().trim().toUpperCase(), 80));
        if (e.linkUrl() != null) s.setLinkUrl(e.linkUrl().isBlank() ? null : clip(e.linkUrl().trim(), 300));
        if (e.linkLabel() != null) s.setLinkLabel(clip(e.linkLabel(), 80));
        if (e.active() != null) s.setActive(e.active());
        // Any edit sends the content back for review.
        s.setReviewStatus("DRAFT");
        s.setLastVerified(null);
        s.setVerifiedBy(null);
        audit.record(adminId, "COMPANION_STEP_EDITED", "COMPANION_STEP", code, null);
        return steps.save(s);
    }

    @Transactional
    public CompanionStep verifyStep(String code, String adminId) {
        CompanionStep s = steps.findById(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found"));
        s.setReviewStatus("VERIFIED");
        s.setLastVerified(LocalDate.now());
        s.setVerifiedBy(adminId);
        audit.record(adminId, "COMPANION_STEP_VERIFIED", "COMPANION_STEP", code, null);
        return steps.save(s);
    }

    @Transactional
    public BusinessProfile verifyBusiness(String farmerId, boolean approve, String adminId, String note) {
        BusinessProfile p = profiles.findById(farmerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        p.setVerified(approve);
        p.setVerifiedAt(approve ? LocalDateTime.now() : null);
        p.setVerifiedBy(approve ? adminId : null);
        profiles.save(p);
        notifications.notify(farmerId, "ACCOUNT", approve ? "Your business is verified" : "Business verification needs attention",
                approve ? "A 'verified business' badge now shows on your Farm Market listings."
                        : (note == null || note.isBlank() ? "Please check your registration number and upload your registration certificate to the vault." : note),
                "companion");
        audit.record(adminId, approve ? "BUSINESS_VERIFIED" : "BUSINESS_VERIFICATION_REJECTED", "BUSINESS_PROFILE", farmerId, note);
        return p;
    }

    @Transactional(readOnly = true)
    public Summary summary(String farmerId) {
        Checklist c = checklist(farmerId);
        List<DeadlineView> upcoming = deadlines(farmerId).stream()
                .filter(d -> "UPCOMING".equals(d.status()) && d.daysLeft() >= 0).toList();
        boolean verified = profiles.findById(farmerId).map(BusinessProfile::isVerified).orElse(false);
        return new Summary(c.percent(), c.done(), c.applicable(), c.intakeCompleted(), verified, upcoming.size(),
                upcoming.isEmpty() ? null : upcoming.get(0));
    }

    // ------------------------------------------------------------------ helpers

    private CropPlanView planView(CropPlan p) {
        String pattern = "%" + p.getCrop().toLowerCase().trim() + "%";
        BigDecimal marketPrice = null;
        String marketUnit = null;
        List<Object[]> sold = orders.soldPrices(pattern, LocalDateTime.now().minusDays(90));
        List<Object[]> asking = sold.isEmpty() ? listings.askingPrices(pattern) : sold;
        Object[] best = asking.stream().filter(row -> p.getYieldUnit().equalsIgnoreCase(String.valueOf(row[0]))).findFirst()
                .orElse(asking.stream().max(Comparator.comparingLong(row -> ((Number) row[2]).longValue())).orElse(null));
        if (best != null && best[1] != null) {
            marketPrice = BigDecimal.valueOf(((Number) best[1]).doubleValue()).setScale(2, RoundingMode.HALF_UP);
            marketUnit = String.valueOf(best[0]);
        }
        BigDecimal actual = null;
        BigDecimal spent = expenses.sumForCrop(p.getFarmerId(), p.getCrop(), LocalDate.now().minusMonths(12));
        if (spent != null && spent.signum() > 0 && p.getHectares().signum() > 0) {
            actual = spent.divide(p.getHectares(), 2, RoundingMode.HALF_UP);
        }
        return new CropPlanView(p.getId(), p.getCrop(), p.getHectares(), p.getYieldPerHa(), p.getYieldUnit(),
                p.getExpectedPrice(), p.getCostPerHa(), p.getPlantMonth(), p.getHarvestMonth(), marketPrice, marketUnit, actual);
    }

    private ProfileView view(BusinessProfile p) {
        return new ProfileView(p.getLegalStructure(), p.getBusinessName(), p.getRegistrationNumber(), p.getRegisteredOn(),
                p.getTaxNumber(), p.isVatRegistered(), p.getVatNumber(), p.getEmployees(), p.getTurnoverBand(), p.getCrops(),
                p.getHectares(), p.isLivestock(), p.getWaterSource(), p.isIrrigates(), p.isUsesAgrochemicals(),
                p.isSellsToRetailers(), p.isExportInterest(), p.isHasBusinessAccount(), p.getFinancialYearEndMonth(),
                p.getProvince(), p.isVerified(), p.getVerifiedAt(), p.getIntakeCompletedAt() != null, OPTIONS);
    }

    private static BusinessProfile blank(String farmerId) {
        BusinessProfile p = new BusinessProfile();
        p.setFarmerId(farmerId);
        return p;
    }

    private static String upper(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v.trim().toUpperCase();
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }
}
