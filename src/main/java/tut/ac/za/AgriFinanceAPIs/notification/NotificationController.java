package tut.ac.za.AgriFinanceAPIs.notification;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.Roles;
import tut.ac.za.AgriFinanceAPIs.supplier.Supplier;
import tut.ac.za.AgriFinanceAPIs.supplier.SupplierRepository;

import java.util.List;

/** Supplier inbox (group-order alerts). Only the supplier's owner or an admin may read it. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    private final SupplierRepository suppliers;

    public NotificationController(NotificationService service, SupplierRepository suppliers) {
        this.service = service;
        this.suppliers = suppliers;
    }

    @GetMapping("/supplier/{supplierId}")
    public List<Notification> forSupplier(@PathVariable String supplierId, Authentication auth) {
        requireOwner(supplierId, auth);
        return service.forSupplier(supplierId);
    }

    @GetMapping("/{id}")
    public Notification get(@PathVariable String id, Authentication auth) {
        Notification n = service.get(id);
        requireOwner(n.getSupplierId(), auth);
        return n;
    }

    @PutMapping("/{id}/read")
    public Notification markRead(@PathVariable String id, Authentication auth) {
        requireOwner(service.get(id).getSupplierId(), auth);
        return service.markRead(id);
    }

    @GetMapping("/supplier/{supplierId}/unread-count")
    public long unread(@PathVariable String supplierId, Authentication auth) {
        requireOwner(supplierId, auth);
        return service.unread(supplierId);
    }

    private void requireOwner(String supplierId, Authentication auth) {
        if (Roles.isAdmin(auth)) return;
        Supplier s = suppliers.findById(supplierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
        if (s.getOwnerId() == null || !s.getOwnerId().equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found");
        }
    }
}
