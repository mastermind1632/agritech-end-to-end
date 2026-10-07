package tut.ac.za.AgriFinanceAPIs.notification;
import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.web.server.ResponseStatusException; import tut.ac.za.AgriFinanceAPIs.supplier.SupplierRepository; import java.util.List;
@Service
public class NotificationService {
 private final NotificationRepository notifications; private final SupplierRepository suppliers;
 public NotificationService(NotificationRepository n,SupplierRepository s){notifications=n;suppliers=s;}
 public Notification createForSupplier(String supplierId,String groupOrderId,String message){if(!suppliers.existsById(supplierId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Supplier not found");Notification n=new Notification();n.setSupplierId(supplierId);n.setGroupOrderId(groupOrderId);n.setMessage(message);return notifications.save(n);}
 public List<Notification> forSupplier(String supplierId){if(!suppliers.existsById(supplierId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Supplier not found");return notifications.findAllBySupplierIdOrderByCreatedAtDesc(supplierId);}
 public Notification get(String id){return notifications.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Notification not found"));}
 public Notification markRead(String id){Notification n=get(id);n.setRead(true);return notifications.save(n);}
 public long unread(String supplierId){if(!suppliers.existsById(supplierId))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Supplier not found");return notifications.countBySupplierIdAndReadFalse(supplierId);}
}
