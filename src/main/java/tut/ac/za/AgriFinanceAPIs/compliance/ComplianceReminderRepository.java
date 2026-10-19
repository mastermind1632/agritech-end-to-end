package tut.ac.za.AgriFinanceAPIs.compliance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ComplianceReminderRepository extends JpaRepository<ComplianceReminder, Long> {
    List<ComplianceReminder> findByFarmerIdOrderByDueDateAsc(String farmerId);
    List<ComplianceReminder> findBySentFalseAndTriggerDateLessThanEqual(LocalDate today);
    void deleteByFarmerId(String farmerId);

    @Query("SELECT b.farmerId FROM BusinessCompliance b " +
           "WHERE b.farmerId NOT IN (SELECT DISTINCT r.farmerId FROM ComplianceReminder r)")
    List<String> findFarmerIdsWithNoReminders();
}
