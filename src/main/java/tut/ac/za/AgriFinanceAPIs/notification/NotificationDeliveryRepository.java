package tut.ac.za.AgriFinanceAPIs.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    List<NotificationDelivery> findByNotificationIdIn(List<Long> ids);
}
