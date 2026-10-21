package tut.ac.za.AgriFinanceAPIs.companion;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tut.ac.za.AgriFinanceAPIs.common.Roles;
import tut.ac.za.AgriFinanceAPIs.companion.CompanionDtos.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class CompanionController {

    private final CompanionService service;

    public CompanionController(CompanionService service) {
        this.service = service;
    }

    @GetMapping("/api/companion/profile")
    public ProfileView profile(Authentication auth) {
        return service.profile(auth.getName());
    }

    @PutMapping("/api/companion/profile")
    public ProfileView saveProfile(@RequestBody ProfileRequest body, Authentication auth) {
        Roles.require(auth, Roles.FARMER, Roles.SUPPLIER);
        return service.saveProfile(auth.getName(), body);
    }

    @GetMapping("/api/companion/checklist")
    public Checklist checklist(Authentication auth) {
        return service.checklist(auth.getName());
    }

    @PutMapping("/api/companion/checklist/{code}")
    public Checklist updateTask(@PathVariable String code, @RequestBody TaskUpdate body, Authentication auth) {
        return service.updateTask(auth.getName(), code, body);
    }

    @GetMapping("/api/companion/summary")
    public Summary summary(Authentication auth) {
        return service.summary(auth.getName());
    }

    @GetMapping("/api/companion/deadlines")
    public List<DeadlineView> deadlines(Authentication auth) {
        return service.deadlines(auth.getName());
    }

    @PostMapping("/api/companion/deadlines")
    @ResponseStatus(HttpStatus.CREATED)
    public DeadlineView addDeadline(@RequestBody DeadlineRequest body, Authentication auth) {
        return service.addDeadline(auth.getName(), body);
    }

    @PutMapping("/api/companion/deadlines/{id}")
    public List<DeadlineView> setStatus(@PathVariable Long id, @RequestBody DeadlineStatus body, Authentication auth) {
        return service.setDeadlineStatus(auth.getName(), id, body.status());
    }

    @DeleteMapping("/api/companion/deadlines/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDeadline(@PathVariable Long id, Authentication auth) {
        service.deleteDeadline(auth.getName(), id);
    }

    @GetMapping("/api/companion/plan")
    public PlanView plan(@RequestParam(required = false) BigDecimal openingCash, Authentication auth) {
        return service.plan(auth.getName(), openingCash);
    }

    @PostMapping("/api/companion/plan/crops")
    @ResponseStatus(HttpStatus.CREATED)
    public CropPlanView addCrop(@RequestBody CropPlanRequest body, Authentication auth) {
        return service.saveCropPlan(auth.getName(), null, body);
    }

    @PutMapping("/api/companion/plan/crops/{id}")
    public CropPlanView updateCrop(@PathVariable Long id, @RequestBody CropPlanRequest body, Authentication auth) {
        return service.saveCropPlan(auth.getName(), id, body);
    }

    @DeleteMapping("/api/companion/plan/crops/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCrop(@PathVariable Long id, Authentication auth) {
        service.deleteCropPlan(auth.getName(), id);
    }

    @GetMapping("/api/companion/plan/export.xlsx")
    public ResponseEntity<byte[]> exportPlan(@RequestParam(required = false) BigDecimal openingCash, Authentication auth) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"farm-business-plan.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.planWorkbook(auth.getName(), openingCash));
    }

    // ---- admin content management (paths under /api/admin require the ADMIN role)

    @GetMapping("/api/admin/companion/steps")
    public List<CompanionStep> steps() {
        return service.allSteps();
    }

    @PutMapping("/api/admin/companion/steps/{code}")
    public CompanionStep editStep(@PathVariable String code, @RequestBody StepEdit body, Authentication auth) {
        return service.editStep(code, body, auth.getName());
    }

    @PostMapping("/api/admin/companion/steps/{code}/verify")
    public CompanionStep verifyStep(@PathVariable String code, Authentication auth) {
        return service.verifyStep(code, auth.getName());
    }

    public record Verification(boolean approve, String note) { }

    @PostMapping("/api/admin/verifications/{farmerId}")
    public ProfileView verify(@PathVariable String farmerId, @RequestBody Verification body, Authentication auth) {
        service.verifyBusiness(farmerId, body.approve(), auth.getName(), body.note());
        return service.profile(farmerId);
    }
}
