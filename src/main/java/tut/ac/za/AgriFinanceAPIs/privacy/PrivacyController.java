package tut.ac.za.AgriFinanceAPIs.privacy;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tut.ac.za.AgriFinanceAPIs.common.AuditLogRepository;
import tut.ac.za.AgriFinanceAPIs.companion.*;
import tut.ac.za.AgriFinanceAPIs.exportready.ExportService;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerService;
import tut.ac.za.AgriFinanceAPIs.farmer.dto.FarmerResponse;
import tut.ac.za.AgriFinanceAPIs.finance.ExpenseRepository;
import tut.ac.za.AgriFinanceAPIs.finance.IncomeRepository;
import tut.ac.za.AgriFinanceAPIs.marketplace.ListingService;
import tut.ac.za.AgriFinanceAPIs.marketplace.OrderService;
import tut.ac.za.AgriFinanceAPIs.notification.NotificationHub;
import tut.ac.za.AgriFinanceAPIs.notification.UserNotificationRepository;
import tut.ac.za.AgriFinanceAPIs.statements.StatementService;
import tut.ac.za.AgriFinanceAPIs.vault.VaultController;
import tut.ac.za.AgriFinanceAPIs.vault.VaultService;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** POPIA self-service: consents, a copy of my data, and a request to delete my account. */
@RestController
@RequestMapping("/api/privacy")
public class PrivacyController {

    private final PrivacyService privacy;
    private final DataRequestRepository requests;
    private final FarmerService farmers;
    private final CompanionService companion;
    private final ExportService exports;
    private final ListingService listings;
    private final OrderService orders;
    private final ExpenseRepository expenses;
    private final IncomeRepository incomes;
    private final StatementService statements;
    private final VaultService vault;
    private final UserNotificationRepository notifications;
    private final AuditLogRepository auditLog;

    public PrivacyController(PrivacyService privacy, DataRequestRepository requests, FarmerService farmers,
                             CompanionService companion, ExportService exports, ListingService listings, OrderService orders,
                             ExpenseRepository expenses, IncomeRepository incomes, StatementService statements,
                             VaultService vault, UserNotificationRepository notifications, AuditLogRepository auditLog) {
        this.privacy = privacy;
        this.requests = requests;
        this.farmers = farmers;
        this.companion = companion;
        this.exports = exports;
        this.listings = listings;
        this.orders = orders;
        this.expenses = expenses;
        this.incomes = incomes;
        this.statements = statements;
        this.vault = vault;
        this.notifications = notifications;
        this.auditLog = auditLog;
    }

    public record ConsentChange(String purpose, boolean granted) { }
    public record DeletionRequestBody(String reason) { }

    @GetMapping("/consents")
    public List<PrivacyService.ConsentView> consents(Authentication auth) {
        return privacy.current(auth.getName());
    }

    @PostMapping("/consents")
    public List<PrivacyService.ConsentView> change(@RequestBody ConsentChange body, Authentication auth) {
        privacy.record(auth.getName(), body.purpose(), body.granted());
        return privacy.current(auth.getName());
    }

    /** Right of access: everything the platform holds about you, as one JSON file (document bytes excluded). */
    @GetMapping("/export")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> export(Authentication auth) {
        String me = auth.getName();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", LocalDateTime.now());
        out.put("account", new FarmerResponse(farmers.getById(me)));
        out.put("consents", privacy.current(me));
        out.put("businessProfile", companion.profile(me));
        out.put("businessChecklist", companion.checklist(me));
        out.put("complianceCalendar", companion.deadlines(me));
        out.put("businessPlan", companion.plan(me, null));
        out.put("exportPlans", exports.list(me));
        out.put("myListings", listings.mine(me));
        out.put("ordersAsBuyer", orders.buying(me));
        out.put("ordersAsSeller", orders.selling(me));
        out.put("expenses", expenses.findAllByFarmerIdOrderByDateDesc(me));
        out.put("income", incomes.findAllByFarmerIdOrderByDateDesc(me));
        out.put("bankStatements", statements.list(me));
        out.put("documents", vault.list(me).stream().map(VaultController::view).toList());
        out.put("notifications", notifications.findByRecipientIdOrderByCreatedAtDesc(me, PageRequest.of(0, 500)).getContent()
                .stream().map(NotificationHub::view).toList());
        out.put("activityLog", auditLog.findByActorIdOrderByCreatedAtDesc(me, PageRequest.of(0, 1000)).getContent());
        out.put("dataRequests", requests.findByFarmerIdOrderByCreatedAtDesc(me));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"my-agritech-data.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(out);
    }

    @PostMapping("/deletion-request")
    @ResponseStatus(HttpStatus.CREATED)
    public DataRequest requestDeletion(@RequestBody(required = false) DeletionRequestBody body, Authentication auth) {
        return privacy.requestDeletion(auth.getName(), body == null ? null : body.reason());
    }

    @GetMapping("/requests")
    public List<DataRequest> myRequests(Authentication auth) {
        return requests.findByFarmerIdOrderByCreatedAtDesc(auth.getName());
    }
}
