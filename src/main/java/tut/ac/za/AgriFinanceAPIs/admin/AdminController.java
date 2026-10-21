package tut.ac.za.AgriFinanceAPIs.admin;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tut.ac.za.AgriFinanceAPIs.common.AuditLog;
import tut.ac.za.AgriFinanceAPIs.common.PageResult;

import java.util.List;
import java.util.Map;

/** Everything under /api/admin requires the ADMIN role (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    public record RoleChange(String role) { }
    public record StatusChange(String status) { }
    public record Reason(String reason) { }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return service.stats();
    }

    @GetMapping("/users")
    public PageResult<AdminService.UserView> users(@RequestParam(required = false) String q, @RequestParam(required = false) String role,
                                                   @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return service.users(q, role, page, size);
    }

    @PutMapping("/users/{id}/role")
    public AdminService.UserView role(@PathVariable String id, @RequestBody RoleChange body, Authentication auth) {
        return service.setRole(id, body.role(), auth.getName());
    }

    @PutMapping("/users/{id}/status")
    public AdminService.UserView status(@PathVariable String id, @RequestBody StatusChange body, Authentication auth) {
        return service.setStatus(id, body.status(), auth.getName());
    }

    @GetMapping("/listings")
    public PageResult<AdminService.ListingAdminView> listings(@RequestParam(required = false) String status,
                                                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return service.listings(status, page, size);
    }

    @PostMapping("/listings/{id}/remove")
    public Map<String, Boolean> removeListing(@PathVariable Long id, @RequestBody Reason body, Authentication auth) {
        service.removeListing(id, body.reason(), auth.getName());
        return Map.of("removed", true);
    }

    @GetMapping("/audit")
    public PageResult<AuditLog> audit(@RequestParam(required = false) String actor, @RequestParam(required = false) String entityType,
                                      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.auditLog(actor, entityType, page, size);
    }

    @GetMapping("/verifications")
    public List<AdminService.VerificationView> verifications() {
        return service.verifications();
    }

    @GetMapping("/data-requests")
    public List<AdminService.DataRequestView> dataRequests(@RequestParam(defaultValue = "true") boolean openOnly) {
        return service.dataRequests(openOnly);
    }

    @PostMapping("/data-requests/{id}/complete")
    public AdminService.DataRequestView completeDeletion(@PathVariable Long id, Authentication auth) {
        return service.completeDeletion(id, auth.getName());
    }
}
