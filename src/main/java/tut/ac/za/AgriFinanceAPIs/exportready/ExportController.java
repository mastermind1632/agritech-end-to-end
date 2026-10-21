package tut.ac.za.AgriFinanceAPIs.exportready;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tut.ac.za.AgriFinanceAPIs.common.Roles;
import tut.ac.za.AgriFinanceAPIs.exportready.ExportDtos.*;

import java.util.List;
import java.util.Map;

@RestController
public class ExportController {

    private final ExportService service;
    private final SourceMonitor monitor;

    public ExportController(ExportService service, SourceMonitor monitor) {
        this.service = service;
        this.monitor = monitor;
    }

    /** Public: products, destinations, authorities and stages (no login needed, used by the resource directory). */
    @GetMapping("/api/public/export/options")
    public Options options() {
        return service.options();
    }

    @GetMapping("/api/public/export/requirements")
    public Map<String, List<RequirementView>> preview(@RequestParam String product, @RequestParam String destination) {
        return service.preview(product, destination);
    }

    @GetMapping("/api/export/workflows")
    public List<WorkflowSummary> list(Authentication auth) {
        return service.list(auth.getName());
    }

    @PostMapping("/api/export/workflows")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowDetail start(@RequestBody StartWorkflow body, Authentication auth) {
        Roles.require(auth, Roles.FARMER);
        return service.start(auth.getName(), body);
    }

    @GetMapping("/api/export/workflows/{id}")
    public WorkflowDetail detail(@PathVariable String id, Authentication auth) {
        return service.detail(id, auth.getName(), Roles.isAdmin(auth));
    }

    @PutMapping("/api/export/workflows/{id}/items/{itemId}")
    public WorkflowDetail updateItem(@PathVariable String id, @PathVariable Long itemId, @RequestBody ItemUpdate body, Authentication auth) {
        return service.updateItem(id, itemId, auth.getName(), body);
    }

    @PostMapping("/api/export/workflows/{id}/advance")
    public WorkflowDetail advance(@PathVariable String id, Authentication auth) {
        return service.advance(id, auth.getName());
    }

    @PostMapping("/api/export/workflows/{id}/close")
    public WorkflowDetail close(@PathVariable String id, @RequestBody CloseWorkflow body, Authentication auth) {
        return service.close(id, auth.getName(), body);
    }

    // ---- compliance editor (admin)

    @GetMapping("/api/admin/export/requirements")
    public List<RequirementView> requirements() {
        return service.allRequirements();
    }

    @PutMapping("/api/admin/export/requirements/{id}")
    public RequirementView edit(@PathVariable Long id, @RequestBody RequirementEdit body, Authentication auth) {
        return service.editRequirement(id, body, auth.getName());
    }

    @PostMapping("/api/admin/export/requirements/{id}/verify")
    public RequirementView verify(@PathVariable Long id, Authentication auth) {
        return service.verifyRequirement(id, auth.getName());
    }

    @GetMapping("/api/admin/export/changes")
    public List<ChangeView> pending() {
        return service.pendingChanges();
    }

    @PostMapping("/api/admin/export/changes")
    @ResponseStatus(HttpStatus.CREATED)
    public ChangeView propose(@RequestBody ChangeProposal body, Authentication auth) {
        return service.propose(body, auth.getName(), "MANUAL");
    }

    public record Review(boolean approve) { }

    @PostMapping("/api/admin/export/changes/{id}/review")
    public ChangeView review(@PathVariable Long id, @RequestBody Review body, Authentication auth) {
        return service.review(id, body.approve(), auth.getName());
    }

    @PostMapping("/api/admin/export/monitor/run")
    public Map<String, Integer> runMonitor() {
        return Map.of("changesDetected", monitor.check(true));
    }
}
