package tut.ac.za.AgriFinanceAPIs.companion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ComplianceDeadlineRepository extends JpaRepository<ComplianceDeadline, Long> {
    List<ComplianceDeadline> findByFarmerIdOrderByDueDateAsc(String farmerId);

    List<ComplianceDeadline> findByStatusAndDueDateLessThanEqual(String status, LocalDate date);

    boolean existsByFarmerIdAndCodeAndDueDate(String farmerId, String code, LocalDate dueDate);

    void deleteByFarmerIdAndCustomFalseAndStatusAndDueDateGreaterThanEqual(String farmerId, String status, LocalDate from);
}
