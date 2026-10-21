package tut.ac.za.AgriFinanceAPIs.admin;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.AuditLog;
import tut.ac.za.AgriFinanceAPIs.common.AuditLogRepository;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;
import tut.ac.za.AgriFinanceAPIs.common.PageResult;
import tut.ac.za.AgriFinanceAPIs.common.Roles;
import tut.ac.za.AgriFinanceAPIs.companion.BusinessProfile;
import tut.ac.za.AgriFinanceAPIs.companion.BusinessProfileRepository;
import tut.ac.za.AgriFinanceAPIs.exportready.ExportRequirementChangeRepository;
import tut.ac.za.AgriFinanceAPIs.exportready.ExportWorkflowRepository;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerAvatarRepository;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;
import tut.ac.za.AgriFinanceAPIs.grouporder.GroupOrderRepository;
import tut.ac.za.AgriFinanceAPIs.marketplace.*;
import tut.ac.za.AgriFinanceAPIs.notification.LiveEvents;
import tut.ac.za.AgriFinanceAPIs.notification.NotificationHub;
import tut.ac.za.AgriFinanceAPIs.privacy.DataRequest;
import tut.ac.za.AgriFinanceAPIs.privacy.DataRequestRepository;
import tut.ac.za.AgriFinanceAPIs.security.RefreshTokenRepository;
import tut.ac.za.AgriFinanceAPIs.statements.BankStatementRepository;
import tut.ac.za.AgriFinanceAPIs.vault.VaultDocumentInfo;
import tut.ac.za.AgriFinanceAPIs.vault.VaultDocumentRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** The admin console: platform metrics, users and roles, moderation, verification queue, audit log, POPIA requests. */
@Service
public class AdminService {

    public record UserView(String id, String name, String contact, String email, String location, String role, String status,
                           boolean mfaEnabled, LocalDateTime createdAt, LocalDateTime lastLoginAt) { }

    public record ListingAdminView(Long id, String product, String sellerId, String sellerName, String status,
                                   BigDecimal pricePerUnit, String unit, BigDecimal quantityAvailable, LocalDateTime createdAt) { }

    public record VerificationView(String farmerId, String farmerName, String businessName, String legalStructure,
                                   String registrationNumber, String taxNumber, LocalDateTime updatedAt, List<Map<String, Object>> documents) { }

    public record DataRequestView(Long id, String farmerId, String farmerName, String type, String status, String reason,
                                  LocalDateTime createdAt, LocalDateTime handledAt) { }

    private final FarmerRepository farmers;
    private final FarmerAvatarRepository avatars;
    private final ListingRepository listings;
    private final MarketOrderRepository orders;
    private final MarketDisputeRepository disputes;
    private final GroupOrderRepository groupOrders;
    private final BusinessProfileRepository profiles;
    private final ExportWorkflowRepository workflows;
    private final ExportRequirementChangeRepository exportChanges;
    private final BankStatementRepository statements;
    private final VaultDocumentRepository documents;
    private final DataRequestRepository dataRequests;
    private final RefreshTokenRepository refreshTokens;
    private final AuditLogRepository auditLog;
    private final AuditService audit;
    private final NotificationHub notifications;
    private final LiveEvents live;

    public AdminService(FarmerRepository farmers, FarmerAvatarRepository avatars, ListingRepository listings,
                        MarketOrderRepository orders, MarketDisputeRepository disputes, GroupOrderRepository groupOrders,
                        BusinessProfileRepository profiles, ExportWorkflowRepository workflows,
                        ExportRequirementChangeRepository exportChanges, BankStatementRepository statements,
                        VaultDocumentRepository documents, DataRequestRepository dataRequests,
                        RefreshTokenRepository refreshTokens, AuditLogRepository auditLog, AuditService audit,
                        NotificationHub notifications, LiveEvents live) {
        this.farmers = farmers;
        this.avatars = avatars;
        this.listings = listings;
        this.orders = orders;
        this.disputes = disputes;
        this.groupOrders = groupOrders;
        this.profiles = profiles;
        this.workflows = workflows;
        this.exportChanges = exportChanges;
        this.statements = statements;
        this.documents = documents;
        this.dataRequests = dataRequests;
        this.refreshTokens = refreshTokens;
        this.auditLog = auditLog;
        this.audit = audit;
        this.notifications = notifications;
        this.live = live;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Long> users = new LinkedHashMap<>();
        for (String r : Roles.ALL) users.put(r, farmers.countByRole(r));
        m.put("users", users);
        m.put("totalUsers", farmers.count());
        m.put("activeListings", listings.countByStatus("ACTIVE"));
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : List.of("PENDING", "CONFIRMED", "DISPATCHED", "DELIVERED", "DISPUTED", "COMPLETED", "CANCELLED", "DECLINED")) {
            byStatus.put(s, orders.countByStatus(s));
        }
        m.put("ordersByStatus", byStatus);
        BigDecimal gmv = orders.completedValue();
        m.put("completedSalesValue", gmv == null ? BigDecimal.ZERO : gmv);
        long completed = byStatus.get("COMPLETED");
        long disputed = disputes.count();
        m.put("disputeRate", completed + disputed == 0 ? 0.0 : Math.round(1000.0 * disputed / (completed + disputed)) / 10.0);
        m.put("openDisputes", disputes.countByStatusIn(List.of("OPEN", "RESPONDED")));
        m.put("openGroupOrders", groupOrders.countByStatus("open"));
        m.put("businessProfiles", profiles.countByIntakeCompletedAtIsNotNull());
        m.put("verifiedBusinesses", profiles.countByVerifiedTrue());
        m.put("pendingVerifications", profiles.awaitingVerification().size());
        m.put("activeExportPlans", workflows.countByStatus("ACTIVE"));
        m.put("shippedExports", workflows.countByStatus("SHIPPED"));
        m.put("pendingContentChanges", exportChanges.countByStatus("PENDING"));
        m.put("statementsConfirmed", statements.countByStatus("CONFIRMED"));
        m.put("statementsInReview", statements.countByStatus("REVIEW"));
        m.put("openDataRequests", dataRequests.findByStatusOrderByCreatedAtAsc("OPEN").size());
        m.put("liveConnections", live.connectedUsers());
        return m;
    }

    @Transactional(readOnly = true)
    public PageResult<UserView> users(String q, String role, int page, int size) {
        String pattern = q == null || q.isBlank() ? "" : "%" + q.trim().toLowerCase() + "%";
        String r = role == null || role.isBlank() ? "" : role.trim().toUpperCase();
        return PageResult.of(farmers.search(pattern, r, PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100))), AdminService::userView);
    }

    @Transactional
    public UserView setRole(String userId, String role, String adminId) {
        String r = role == null ? "" : role.trim().toUpperCase();
        if (!Roles.ALL.contains(r)) throw bad("Role must be one of " + Roles.ALL);
        if (userId.equals(adminId) && !Roles.ADMIN.equals(r)) throw bad("You cannot remove your own admin role");
        Farmer f = farmers.findById(userId).orElseThrow(() -> notFound("User not found"));
        String old = f.getRole();
        f.setRole(r);
        farmers.save(f);
        refreshTokens.revokeAll(userId, LocalDateTime.now());
        audit.record(adminId, "ROLE_CHANGED", "FARMER", userId, old + " -> " + r);
        notifications.notify(userId, "ACCOUNT", "Your account type changed", "You are now a " + r.toLowerCase() + ". Please sign in again.", "profile");
        return userView(f);
    }

    @Transactional
    public UserView setStatus(String userId, String status, String adminId) {
        String s = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("ACTIVE", "SUSPENDED").contains(s)) throw bad("Status must be ACTIVE or SUSPENDED");
        if (userId.equals(adminId)) throw bad("You cannot suspend yourself");
        Farmer f = farmers.findById(userId).orElseThrow(() -> notFound("User not found"));
        f.setStatus(s);
        farmers.save(f);
        if ("SUSPENDED".equals(s)) refreshTokens.revokeAll(userId, LocalDateTime.now());
        audit.record(adminId, "USER_" + s, "FARMER", userId, null);
        return userView(f);
    }

    @Transactional(readOnly = true)
    public PageResult<ListingAdminView> listings(String status, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        org.springframework.data.domain.Page<Listing> result;
        if (status == null || status.isBlank()) {
            result = listings.findAll(pageable);
        } else {
            String wanted = status.trim().toUpperCase();
            org.springframework.data.jpa.domain.Specification<Listing> byStatus = (root, query, cb) -> cb.equal(root.get("status"), wanted);
            result = listings.findAll(byStatus, pageable);
        }
        Set<String> ids = result.getContent().stream().map(Listing::getSellerId).collect(Collectors.toSet());
        Map<String, String> names = farmers.findAllById(ids).stream().collect(Collectors.toMap(Farmer::getId, Farmer::getName));
        return PageResult.of(result, l -> new ListingAdminView(l.getId(), l.getProduct(), l.getSellerId(),
                names.getOrDefault(l.getSellerId(), "Farmer"), l.getStatus(), l.getPricePerUnit(), l.getUnit(),
                l.getQuantityAvailable(), l.getCreatedAt()));
    }

    @Transactional
    public void removeListing(Long id, String reason, String adminId) {
        Listing l = listings.findById(id).orElseThrow(() -> notFound("Listing not found"));
        if (reason == null || reason.isBlank()) throw bad("Give the seller a reason");
        l.setStatus("REMOVED");
        listings.save(l);
        notifications.notify(l.getSellerId(), "ORDER", "Your listing '" + l.getProduct() + "' was removed by a moderator", reason, "farmmarket");
        audit.record(adminId, "LISTING_REMOVED", "LISTING", String.valueOf(id), reason);
    }

    @Transactional(readOnly = true)
    public PageResult<AuditLog> auditLog(String actor, String entityType, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 200));
        if (actor != null && !actor.isBlank()) return PageResult.of(auditLog.findByActorIdOrderByCreatedAtDesc(actor, pageable), a -> a);
        if (entityType != null && !entityType.isBlank()) return PageResult.of(auditLog.findByEntityTypeOrderByCreatedAtDesc(entityType.toUpperCase(), pageable), a -> a);
        return PageResult.of(auditLog.findAllByOrderByCreatedAtDesc(pageable), a -> a);
    }

    @Transactional(readOnly = true)
    public List<VerificationView> verifications() {
        List<BusinessProfile> list = profiles.awaitingVerification();
        Map<String, String> names = farmers.findAllById(list.stream().map(BusinessProfile::getFarmerId).toList()).stream()
                .collect(Collectors.toMap(Farmer::getId, Farmer::getName));
        List<VerificationView> out = new ArrayList<>();
        for (BusinessProfile p : list) {
            List<Map<String, Object>> docs = documents.listForOwner(p.getFarmerId()).stream()
                    .filter(d -> Set.of("CIPC_CERTIFICATE", "TAX_CLEARANCE", "VAT_REGISTRATION", "ID_DOCUMENT", "BANK_LETTER").contains(d.docType()))
                    .map(tut.ac.za.AgriFinanceAPIs.vault.VaultController::view).toList();
            out.add(new VerificationView(p.getFarmerId(), names.getOrDefault(p.getFarmerId(), "Farmer"), p.getBusinessName(),
                    p.getLegalStructure(), p.getRegistrationNumber(), p.getTaxNumber(), p.getUpdatedAt(), docs));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<DataRequestView> dataRequests(boolean openOnly) {
        List<DataRequest> list = openOnly ? dataRequests.findByStatusOrderByCreatedAtAsc("OPEN") : dataRequests.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<String, String> names = farmers.findAllById(list.stream().map(DataRequest::getFarmerId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Farmer::getId, Farmer::getName));
        return list.stream().map(r -> new DataRequestView(r.getId(), r.getFarmerId(), names.getOrDefault(r.getFarmerId(), "User"),
                r.getRequestType(), r.getStatus(), r.getReason(), r.getCreatedAt(), r.getHandledAt())).toList();
    }

    /**
     * Completes a deletion request by anonymising the account. Orders stay (the other party and SARS record-keeping
     * need them) but no longer identify the person; documents, statements and the business profile are deleted.
     */
    @Transactional
    public DataRequestView completeDeletion(Long requestId, String adminId) {
        DataRequest r = dataRequests.findById(requestId).orElseThrow(() -> notFound("Request not found"));
        if (!"OPEN".equals(r.getStatus())) throw bad("This request is already handled");
        Farmer f = farmers.findById(r.getFarmerId()).orElseThrow(() -> notFound("User not found"));
        for (VaultDocumentInfo d : documents.listForOwner(f.getId())) documents.deleteById(d.id());
        statements.findByFarmerIdOrderByCreatedAtDesc(f.getId()).forEach(statements::delete);
        profiles.findById(f.getId()).ifPresent(profiles::delete);
        if (avatars.existsById(f.getId())) avatars.deleteById(f.getId());
        f.setName("Deleted user");
        f.setContact("deleted-" + f.getId());
        f.setEmail(null);
        f.setLocation(null);
        f.setPasswordHash(null);
        f.setMfaEnabled(false);
        f.setMfaSecret(null);
        f.setStatus("SUSPENDED");
        farmers.save(f);
        refreshTokens.revokeAll(f.getId(), LocalDateTime.now());
        r.setStatus("COMPLETED");
        r.setHandledBy(adminId);
        r.setHandledAt(LocalDateTime.now());
        dataRequests.save(r);
        audit.record(adminId, "ACCOUNT_ANONYMISED", "FARMER", f.getId(), "data request " + requestId);
        return dataRequests(false).stream().filter(v -> v.id().equals(requestId)).findFirst().orElseThrow();
    }

    private static UserView userView(Farmer f) {
        return new UserView(f.getId(), f.getName(), f.getContact(), f.getEmail(), f.getLocation(), f.getRole(), f.getStatus(),
                f.isMfaEnabled(), f.getCreatedAt(), f.getLastLoginAt());
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

    private static ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
}
