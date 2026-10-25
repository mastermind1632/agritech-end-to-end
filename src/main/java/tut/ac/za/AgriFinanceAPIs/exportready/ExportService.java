package tut.ac.za.AgriFinanceAPIs.exportready;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;
import tut.ac.za.AgriFinanceAPIs.companion.BusinessProfile;
import tut.ac.za.AgriFinanceAPIs.companion.BusinessProfileRepository;
import tut.ac.za.AgriFinanceAPIs.exportready.ExportDtos.*;
import tut.ac.za.AgriFinanceAPIs.notification.NotificationHub;
import tut.ac.za.AgriFinanceAPIs.vault.VaultDocumentInfo;
import tut.ac.za.AgriFinanceAPIs.vault.VaultDocumentRepository;
import tut.ac.za.AgriFinanceAPIs.vault.VaultService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Module 4: PPECB export readiness. A workflow from "interested in exporting" to "shipment cleared" in which every
 * requirement says which authority it comes from, so the farmer knows whom to contact when a step is blocked.
 */
@Service
public class ExportService {

    public static final String DISCLAIMER = "Export requirements change. Every item links to its official source; items marked "
            + "DRAFT have not yet been confirmed by our compliance reviewer. Confirm current procedures with PPECB, the "
            + "Department of Agriculture, SARS Customs and your freight forwarder before shipping. This is guidance, not legal advice.";
    static final Set<String> ITEM_STATUSES = Set.of("TODO", "IN_PROGRESS", "BLOCKED", "DONE", "NOT_APPLICABLE");

    private final ExportProductRepository products;
    private final ExportDestinationRepository destinations;
    private final ExportRequirementRepository requirements;
    private final ExportRequirementChangeRepository changes;
    private final ExportWorkflowRepository workflows;
    private final ExportWorkflowItemRepository items;
    private final BusinessProfileRepository profiles;
    private final VaultService vault;
    private final VaultDocumentRepository documents;
    private final NotificationHub notifications;
    private final AuditService audit;

    public ExportService(ExportProductRepository products, ExportDestinationRepository destinations,
                         ExportRequirementRepository requirements, ExportRequirementChangeRepository changes,
                         ExportWorkflowRepository workflows, ExportWorkflowItemRepository items,
                         BusinessProfileRepository profiles, VaultService vault, VaultDocumentRepository documents,
                         NotificationHub notifications, AuditService audit) {
        this.products = products;
        this.destinations = destinations;
        this.requirements = requirements;
        this.changes = changes;
        this.workflows = workflows;
        this.items = items;
        this.profiles = profiles;
        this.vault = vault;
        this.documents = documents;
        this.notifications = notifications;
        this.audit = audit;
    }

    public Options options() {
        return new Options(products.findByActiveTrueOrderByNameAsc(), destinations.findByActiveTrueOrderByNameAsc(),
                ExportReadiness.AUTHORITIES, ExportReadiness.STAGES);
    }

    /** The checklist for a product and destination before starting (also public, for the resource directory). */
    @Transactional(readOnly = true)
    public Map<String, List<RequirementView>> preview(String product, String destination) {
        requireProduct(product);
        requireDestination(destination);
        Map<String, List<RequirementView>> out = new LinkedHashMap<>();
        for (String a : ExportReadiness.AUTHORITIES.keySet()) out.put(a, new ArrayList<>());
        for (ExportRequirement r : requirements.forPair(product, destination)) {
            out.computeIfAbsent(r.getAuthority(), k -> new ArrayList<>()).add(view(r));
        }
        out.values().removeIf(List::isEmpty);
        return out;
    }

    @Transactional
    public WorkflowDetail start(String farmerId, StartWorkflow req) {
        if (req == null) throw bad("Choose a product and destination");
        ExportProduct p = requireProduct(req.productCode());
        ExportDestination d = requireDestination(req.destinationCode());
        boolean duplicate = workflows.findByFarmerIdOrderByCreatedAtDesc(farmerId).stream()
                .anyMatch(w -> "ACTIVE".equals(w.getStatus()) && w.getProductCode().equals(p.getCode()) && w.getDestinationCode().equals(d.getCode()));
        if (duplicate) throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have an active export plan for this product and destination");
        if (req.volume() != null && req.volume().signum() <= 0) throw bad("Volume must be greater than zero");

        ExportWorkflow w = new ExportWorkflow();
        w.setFarmerId(farmerId);
        w.setProductCode(p.getCode());
        w.setDestinationCode(d.getCode());
        w.setTargetShipDate(req.targetShipDate());
        w.setVolume(req.volume());
        w.setVolumeUnit(req.volumeUnit() == null ? null : req.volumeUnit().trim());
        
        // --- SOP SPECIFIC FIELDS ---
        w.setFboCode(req.fboCode());
        w.setVariety(req.variety());
        w.setClassCategory(req.classCategory());
        w.setPackageCount(req.packageCount());
        w.setPackageType(req.packageType());
        w.setPalletCount(req.palletCount());
        w.setContainerNumber(req.containerNumber());
        w.setTransportMode(req.transportMode());
        
        // SOP Rule: Validity is 40 days by Sea/Land, 10 days by Air
        if (req.transportMode() != null) {
            java.time.LocalDate inspectionDate = java.time.LocalDate.now(); // Default to today
            w.setInspectionDate(inspectionDate);
            
            if ("AIR".equalsIgnoreCase(req.transportMode())) {
                w.setCertificateValidUntil(inspectionDate.plusDays(10));
            } else if ("SEA".equalsIgnoreCase(req.transportMode()) || "LAND".equalsIgnoreCase(req.transportMode())) {
                w.setCertificateValidUntil(inspectionDate.plusDays(40));
            }
        }
        
        workflows.save(w);
        for (ExportRequirement r : requirements.forPair(p.getCode(), d.getCode())) {
            ExportWorkflowItem i = new ExportWorkflowItem();
            i.setWorkflowId(w.getId());
            i.setRequirementId(r.getId());
            i.setRequirementVersion(r.getVersion());
            items.save(i);
        }
        autoCheckEligibility(w);
        audit.record(farmerId, "EXPORT_WORKFLOW_STARTED", "EXPORT_WORKFLOW", w.getId(), p.getCode() + " -> " + d.getCode());
        return detail(w.getId(), farmerId, false);
    }

    @Transactional(readOnly = true)
    public List<WorkflowSummary> list(String farmerId) {
        List<ExportWorkflow> ws = workflows.findByFarmerIdOrderByCreatedAtDesc(farmerId);
        if (ws.isEmpty()) return List.of();
        Map<String, List<ExportWorkflowItem>> byWf = items.findByWorkflowIdIn(ws.stream().map(ExportWorkflow::getId).toList())
                .stream().collect(Collectors.groupingBy(ExportWorkflowItem::getWorkflowId));
        Map<Long, ExportRequirement> reqs = requirementsById(byWf.values().stream().flatMap(List::stream).map(ExportWorkflowItem::getRequirementId).collect(Collectors.toSet()));
        return ws.stream().map(w -> summary(w, byWf.getOrDefault(w.getId(), List.of()), reqs)).toList();
    }

    @Transactional
    public WorkflowDetail detail(String workflowId, String viewer, boolean admin) {
        ExportWorkflow w = owned(workflowId, viewer, admin);
        if ("ACTIVE".equals(w.getStatus())) autoCheckEligibility(w);
        List<ExportWorkflowItem> list = items.findByWorkflowId(w.getId());
        Map<Long, ExportRequirement> reqs = requirementsById(list.stream().map(ExportWorkflowItem::getRequirementId).collect(Collectors.toSet()));
        Set<String> docIds = list.stream().map(ExportWorkflowItem::getDocumentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> docTitles = docIds.isEmpty() ? Map.of() : documents.infos(docIds).stream()
                .collect(Collectors.toMap(VaultDocumentInfo::id, VaultDocumentInfo::title));

        List<ItemView> itemViews = new ArrayList<>();
        for (ExportWorkflowItem i : list) {
            ExportRequirement r = reqs.get(i.getRequirementId());
            if (r == null) continue;
            itemViews.add(new ItemView(i.getId(), view(r), i.getStatus(), i.getNote(), i.getDocumentId(),
                    i.getDocumentId() == null ? null : docTitles.get(i.getDocumentId()), i.getCompletedAt(),
                    r.getVersion() > i.getRequirementVersion(), isAuto(r)));
        }
        itemViews.sort(Comparator.comparingInt((ItemView v) -> v.requirement().stage()).thenComparing(v -> v.requirement().id()));

        List<ExportReadiness.Item> scoring = itemViews.stream().map(v -> new ExportReadiness.Item(v.requirement().stage(), v.status())).toList();
        ExportReadiness.Score score = ExportReadiness.score(scoring);
        List<StageView> stages = new ArrayList<>();
        for (int s = 1; s <= ExportReadiness.STAGES.size(); s++) {
            final int st = s;
            List<ItemView> inStage = itemViews.stream().filter(v -> v.requirement().stage() == st).toList();
            stages.add(new StageView(s, ExportReadiness.STAGES.get(s - 1), score.stagePercent().getOrDefault(s, 100),
                    s == w.getCurrentStage(), ExportReadiness.stageComplete(scoring, s), inStage));
        }
        Map<String, List<ItemView>> byAuthority = new LinkedHashMap<>();
        Map<String, Integer> authorityPercent = new LinkedHashMap<>();
        for (String a : ExportReadiness.AUTHORITIES.keySet()) {
            List<ItemView> forA = itemViews.stream().filter(v -> v.requirement().authority().equals(a)).toList();
            if (forA.isEmpty()) continue;
            byAuthority.put(a, forA);
            long applicable = forA.stream().filter(v -> !"NOT_APPLICABLE".equals(v.status())).count();
            long done = forA.stream().filter(v -> "DONE".equals(v.status())).count();
            authorityPercent.put(a, applicable == 0 ? 100 : (int) Math.round(100.0 * done / applicable));
        }
        List<String> blockers = itemViews.stream()
                .filter(v -> v.requirement().stage() == w.getCurrentStage() && !"DONE".equals(v.status()) && !"NOT_APPLICABLE".equals(v.status()))
                .map(v -> v.requirement().title() + " (" + v.requirement().authorityLabel() + ")").toList();
        return new WorkflowDetail(summary(w, list, reqs), stages, byAuthority, authorityPercent,
                "ACTIVE".equals(w.getStatus()) && ExportReadiness.canAdvance(scoring, w.getCurrentStage()), blockers,
                w.getOutcome(), w.getLessons(), w.getVolume(), w.getVolumeUnit(), DISCLAIMER);
    }

    @Transactional
    public WorkflowDetail updateItem(String workflowId, Long itemId, String farmerId, ItemUpdate u) {
        ExportWorkflow w = owned(workflowId, farmerId, false);
        if (!"ACTIVE".equals(w.getStatus())) throw conflict("This export plan is closed");
        ExportWorkflowItem i = items.findById(itemId).filter(x -> x.getWorkflowId().equals(workflowId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Checklist item not found"));
        String status = u == null || u.status() == null ? i.getStatus() : u.status().trim().toUpperCase();
        if (!ITEM_STATUSES.contains(status)) throw bad("Status must be TODO, IN_PROGRESS, BLOCKED, DONE or NOT_APPLICABLE");
        if (u != null && u.documentId() != null) {
            if (u.documentId().isBlank()) i.setDocumentId(null);
            else {
                vault.requireOwned(u.documentId(), farmerId);
                i.setDocumentId(u.documentId());
            }
        }
        if (u != null && u.note() != null) i.setNote(u.note().length() > 500 ? u.note().substring(0, 500) : u.note());
        i.setStatus(status);
        i.setCompletedAt("DONE".equals(status) ? LocalDateTime.now() : null);
        // Working against the current version of the requirement clears the "requirement changed" flag.
        requirements.findById(i.getRequirementId()).ifPresent(r -> i.setRequirementVersion(r.getVersion()));
        items.save(i);
        return detail(workflowId, farmerId, false);
    }

    @Transactional
    public WorkflowDetail advance(String workflowId, String farmerId) {
        ExportWorkflow w = owned(workflowId, farmerId, false);
        if (!"ACTIVE".equals(w.getStatus())) throw conflict("This export plan is closed");
        List<ExportReadiness.Item> scoring = scoring(w);
        if (!ExportReadiness.canAdvance(scoring, w.getCurrentStage())) {
            throw conflict("Finish (or mark as not applicable) every item in '" + ExportReadiness.STAGES.get(w.getCurrentStage() - 1) + "' first");
        }
        w.setCurrentStage(w.getCurrentStage() + 1);
        workflows.save(w);
        audit.record(farmerId, "EXPORT_STAGE_ADVANCED", "EXPORT_WORKFLOW", workflowId, "stage " + w.getCurrentStage());
        if (w.getCurrentStage() == 7 && ExportReadiness.exportReady(scoring)) {
            notifications.notify(farmerId, "EXPORT", "Export-ready", "You have completed every step up to inspection. "
                    + "An 'export-ready' badge now shows on your Farm Market profile.", "export");
        }
        return detail(workflowId, farmerId, false);
    }

    @Transactional
    public WorkflowDetail close(String workflowId, String farmerId, CloseWorkflow req) {
        ExportWorkflow w = owned(workflowId, farmerId, false);
        if (!"ACTIVE".equals(w.getStatus())) throw conflict("This export plan is already closed");
        boolean cancelled = req != null && req.cancelled();
        if (!cancelled && w.getCurrentStage() < ExportReadiness.STAGES.size()) {
            throw conflict("Move through every stage before closing as shipped, or cancel the plan");
        }
        w.setStatus(cancelled ? "CANCELLED" : "SHIPPED");
        w.setClosedAt(LocalDateTime.now());
        if (req != null) {
            w.setOutcome(clip(req.outcome(), 1000));
            w.setLessons(clip(req.lessons(), 1000));
        }
        workflows.save(w);
        audit.record(farmerId, cancelled ? "EXPORT_WORKFLOW_CANCELLED" : "EXPORT_WORKFLOW_SHIPPED", "EXPORT_WORKFLOW", workflowId, null);
        return detail(workflowId, farmerId, false);
    }

    /** Farmers among the given ids who have shipped, or completed everything up to inspection. */
    @Transactional(readOnly = true)
    public Set<String> exportReady(Collection<String> farmerIds) {
        if (farmerIds.isEmpty()) return Set.of();
        List<ExportWorkflow> ws = workflows.findByFarmerIdInAndStatusIn(farmerIds, List.of("ACTIVE", "SHIPPED"));
        Set<String> out = new HashSet<>();
        List<ExportWorkflow> active = new ArrayList<>();
        for (ExportWorkflow w : ws) {
            if ("SHIPPED".equals(w.getStatus())) out.add(w.getFarmerId());
            else if (w.getCurrentStage() >= 7) active.add(w);
        }
        if (!active.isEmpty()) {
            Map<String, List<ExportWorkflowItem>> byWf = items.findByWorkflowIdIn(active.stream().map(ExportWorkflow::getId).toList())
                    .stream().collect(Collectors.groupingBy(ExportWorkflowItem::getWorkflowId));
            Map<Long, ExportRequirement> reqs = requirementsById(byWf.values().stream().flatMap(List::stream)
                    .map(ExportWorkflowItem::getRequirementId).collect(Collectors.toSet()));
            for (ExportWorkflow w : active) {
                List<ExportReadiness.Item> sc = byWf.getOrDefault(w.getId(), List.of()).stream()
                        .filter(i -> reqs.containsKey(i.getRequirementId()))
                        .map(i -> new ExportReadiness.Item(reqs.get(i.getRequirementId()).getStage(), i.getStatus())).toList();
                if (ExportReadiness.exportReady(sc)) out.add(w.getFarmerId());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ admin: requirement content

    public List<RequirementView> allRequirements() {
        return requirements.findAllByOrderByStageAscIdAsc().stream().map(this::view).toList();
    }

    /** Direct edit by a compliance editor: the version goes up and farmers with an active plan are told. */
    @Transactional
    public RequirementView editRequirement(Long id, RequirementEdit e, String adminId) {
        ExportRequirement r = requirements.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requirement not found"));
        if (e.title() != null && !e.title().isBlank()) r.setTitle(clip(e.title(), 200));
        if (e.description() != null && !e.description().isBlank()) r.setDescription(clip(e.description(), 2000));
        if (e.documents() != null) r.setDocuments(clip(e.documents(), 500));
        if (e.leadTimeDays() != null) r.setLeadTimeDays(e.leadTimeDays() < 0 ? null : e.leadTimeDays());
        if (e.estCost() != null) r.setEstCost(clip(e.estCost(), 80));
        if (e.sourceUrl() != null) r.setSourceUrl(e.sourceUrl().isBlank() ? null : clip(e.sourceUrl(), 300));
        if (e.active() != null) r.setActive(e.active());
        r.setVersion(r.getVersion() + 1);
        r.setReviewStatus("VERIFIED");
        r.setLastVerified(LocalDate.now());
        r.setVerifiedBy(adminId);
        requirements.save(r);
        notifyAffected(r, e.changeSummary());
        audit.record(adminId, "EXPORT_REQUIREMENT_EDITED", "EXPORT_REQUIREMENT", String.valueOf(id), "v" + r.getVersion());
        return view(r);
    }

    @Transactional
    public RequirementView verifyRequirement(Long id, String adminId) {
        ExportRequirement r = requirements.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requirement not found"));
        r.setReviewStatus("VERIFIED");
        r.setLastVerified(LocalDate.now());
        r.setVerifiedBy(adminId);
        audit.record(adminId, "EXPORT_REQUIREMENT_VERIFIED", "EXPORT_REQUIREMENT", String.valueOf(id), null);
        return view(requirements.save(r));
    }

    @Transactional
    public ChangeView propose(ChangeProposal p, String proposer, String source) {
        ExportRequirement r = requirements.findById(p.requirementId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Requirement not found"));
        if (p.summary() == null || p.summary().isBlank()) throw bad("Summarise what changed");
        ExportRequirementChange c = new ExportRequirementChange();
        c.setRequirementId(r.getId());
        c.setSource(source);
        c.setSummary(clip(p.summary(), 500));
        c.setProposedTitle(clip(p.proposedTitle(), 200));
        c.setProposedDescription(clip(p.proposedDescription(), 2000));
        c.setStatus("PENDING");
        c.setProposedBy(proposer);
        changes.save(c);
        return changeView(c, r);
    }

    public List<ChangeView> pendingChanges() {
        List<ExportRequirementChange> list = changes.findByStatusOrderByCreatedAtAsc("PENDING");
        Map<Long, ExportRequirement> reqs = requirementsById(list.stream().map(ExportRequirementChange::getRequirementId).collect(Collectors.toSet()));
        return list.stream().map(c -> changeView(c, reqs.get(c.getRequirementId()))).toList();
    }

    /** Only after a person approves a change are the requirement and the affected farmers updated. */
    @Transactional
    public ChangeView review(Long changeId, boolean approve, String adminId) {
        ExportRequirementChange c = changes.findById(changeId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Change not found"));
        if (!"PENDING".equals(c.getStatus())) throw conflict("This change has already been reviewed");
        ExportRequirement r = requirements.findById(c.getRequirementId()).orElseThrow();
        c.setStatus(approve ? "APPROVED" : "REJECTED");
        c.setReviewedBy(adminId);
        c.setReviewedAt(LocalDateTime.now());
        changes.save(c);
        if (approve && (c.getProposedTitle() != null || c.getProposedDescription() != null)) {
            editRequirement(r.getId(), new RequirementEdit(c.getProposedTitle(), c.getProposedDescription(), null, null, null, null, null,
                    c.getSummary()), adminId);
        } else if (approve) {
            r.setReviewStatus("VERIFIED");
            r.setLastVerified(LocalDate.now());
            r.setVerifiedBy(adminId);
            requirements.save(r);
        }
        audit.record(adminId, approve ? "EXPORT_CHANGE_APPROVED" : "EXPORT_CHANGE_REJECTED", "EXPORT_CHANGE", String.valueOf(changeId), null);
        return changeView(c, r);
    }

    private void notifyAffected(ExportRequirement r, String summary) {
        Set<String> told = new HashSet<>();
        for (ExportWorkflow w : workflows.findByStatus("ACTIVE")) {
            if (!r.appliesTo(w.getProductCode(), w.getDestinationCode())) continue;
            boolean hasItem = items.findByWorkflowIdAndRequirementId(w.getId(), r.getId()).isPresent();
            if (!hasItem && r.isActive()) {
                ExportWorkflowItem i = new ExportWorkflowItem();
                i.setWorkflowId(w.getId());
                i.setRequirementId(r.getId());
                i.setRequirementVersion(0);
                items.save(i);
            }
            if (told.add(w.getFarmerId())) {
                notifications.notify(w.getFarmerId(), "EXPORT", "Export requirement updated: " + r.getTitle(),
                        (summary == null || summary.isBlank() ? "A requirement in your export plan has changed." : summary)
                                + " Review it in Export Readiness.", "export");
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Stage 1 items are filled from the Business Companion: registration number and tax number. */
    private void autoCheckEligibility(ExportWorkflow w) {
        BusinessProfile p = profiles.findById(w.getFarmerId()).orElse(null);
        if (p == null) return;
        for (ExportWorkflowItem i : items.findByWorkflowId(w.getId())) {
            ExportRequirement r = requirements.findById(i.getRequirementId()).orElse(null);
            if (r == null || !"TODO".equals(i.getStatus())) continue;
            boolean met = ("ELIG_REGISTERED".equals(r.getCode()) && p.getRegistrationNumber() != null)
                    || ("ELIG_TAX".equals(r.getCode()) && p.getTaxNumber() != null);
            if (met) {
                i.setStatus("DONE");
                i.setNote("Confirmed from your Business Companion profile");
                i.setCompletedAt(LocalDateTime.now());
                items.save(i);
            }
        }
    }

    private static boolean isAuto(ExportRequirement r) {
        return "ELIG_REGISTERED".equals(r.getCode()) || "ELIG_TAX".equals(r.getCode());
    }

    private List<ExportReadiness.Item> scoring(ExportWorkflow w) {
        List<ExportWorkflowItem> list = items.findByWorkflowId(w.getId());
        Map<Long, ExportRequirement> reqs = requirementsById(list.stream().map(ExportWorkflowItem::getRequirementId).collect(Collectors.toSet()));
        return list.stream().filter(i -> reqs.containsKey(i.getRequirementId()))
                .map(i -> new ExportReadiness.Item(reqs.get(i.getRequirementId()).getStage(), i.getStatus())).toList();
    }

    private WorkflowSummary summary(ExportWorkflow w, List<ExportWorkflowItem> list, Map<Long, ExportRequirement> reqs) {
        List<ExportReadiness.Item> sc = list.stream().filter(i -> reqs.containsKey(i.getRequirementId()))
                .map(i -> new ExportReadiness.Item(reqs.get(i.getRequirementId()).getStage(), i.getStatus())).toList();
        long changed = list.stream().filter(i -> reqs.containsKey(i.getRequirementId())
                && reqs.get(i.getRequirementId()).getVersion() > i.getRequirementVersion()).count();
        ExportProduct p = products.findById(w.getProductCode()).orElse(null);
        ExportDestination d = destinations.findById(w.getDestinationCode()).orElse(null);
        int stage = Math.min(Math.max(w.getCurrentStage(), 1), ExportReadiness.STAGES.size());
        return new WorkflowSummary(w.getId(), w.getProductCode(), p == null ? w.getProductCode() : p.getName(),
                p == null ? null : p.getHsCode(), w.getDestinationCode(), d == null ? w.getDestinationCode() : d.getName(),
                w.getCurrentStage(), ExportReadiness.STAGES.get(stage - 1), w.getStatus(), ExportReadiness.score(sc).percent(),
                "SHIPPED".equals(w.getStatus()) || ExportReadiness.exportReady(sc), w.getTargetShipDate(), w.getCreatedAt(), changed);
    }

    private RequirementView view(ExportRequirement r) {
        return new RequirementView(r.getId(), r.getCode(), r.getStage(), ExportReadiness.STAGES.get(Math.min(Math.max(r.getStage(), 1), 8) - 1),
                r.getAuthority(), ExportReadiness.AUTHORITIES.getOrDefault(r.getAuthority(), r.getAuthority()), r.getTitle(),
                r.getDescription(), r.getDocuments(), r.getLeadTimeDays(), r.getEstCost(), r.getSourceUrl(), r.getReviewStatus(),
                r.getLastVerified(), r.getProductCode(), r.getDestinationCode(), r.getVersion());
    }

    private ChangeView changeView(ExportRequirementChange c, ExportRequirement r) {
        return new ChangeView(c.getId(), c.getRequirementId(), r == null ? null : r.getTitle(), c.getSource(), c.getSummary(),
                c.getProposedTitle(), c.getProposedDescription(), c.getStatus(), c.getCreatedAt());
    }

    private Map<Long, ExportRequirement> requirementsById(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return requirements.findAllById(ids).stream().collect(Collectors.toMap(ExportRequirement::getId, r -> r));
    }

    private ExportWorkflow owned(String id, String viewer, boolean admin) {
        ExportWorkflow w = workflows.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Export plan not found"));
        if (!admin && !w.getFarmerId().equals(viewer)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Export plan not found");
        return w;
    }

    private ExportProduct requireProduct(String code) {
        if (code == null || code.isBlank()) throw bad("Choose a product");
        return products.findById(code.trim().toUpperCase()).filter(ExportProduct::isActive)
                .orElseThrow(() -> bad("That product is not supported yet"));
    }

    private ExportDestination requireDestination(String code) {
        if (code == null || code.isBlank()) throw bad("Choose a destination");
        return destinations.findById(code.trim().toUpperCase()).filter(ExportDestination::isActive)
                .orElseThrow(() -> bad("That destination is not supported yet"));
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

    private static ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }
}

