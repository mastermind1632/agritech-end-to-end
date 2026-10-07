package tut.ac.za.AgriFinanceAPIs.notification;
import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/notifications")
public class NotificationController {
 private final NotificationService service; public NotificationController(NotificationService s){service=s;}
 @GetMapping("/supplier/{supplierId}") public List<Notification> forSupplier(@PathVariable String supplierId){return service.forSupplier(supplierId);}
 @GetMapping("/{id}") public Notification get(@PathVariable String id){return service.get(id);}
 @PutMapping("/{id}/read") public Notification markRead(@PathVariable String id){return service.markRead(id);}
 @GetMapping("/supplier/{supplierId}/unread-count") public long unread(@PathVariable String supplierId){return service.unread(supplierId);}
}
