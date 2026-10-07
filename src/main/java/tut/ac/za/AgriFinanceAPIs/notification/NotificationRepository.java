package tut.ac.za.AgriFinanceAPIs.notification;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface NotificationRepository extends JpaRepository<Notification,String>{List<Notification> findAllBySupplierIdOrderByCreatedAtDesc(String supplierId); long countBySupplierIdAndReadFalse(String supplierId);}
