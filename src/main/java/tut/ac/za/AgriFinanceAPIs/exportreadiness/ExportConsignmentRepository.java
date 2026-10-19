package tut.ac.za.AgriFinanceAPIs.exportreadiness;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExportConsignmentRepository extends JpaRepository<ExportConsignment, Long> {
    List<ExportConsignment> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
}
