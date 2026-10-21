package tut.ac.za.AgriFinanceAPIs.notification;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> { }
