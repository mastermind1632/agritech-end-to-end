package tut.ac.za.AgriFinanceAPIs.exportready;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExportRequirementChangeRepository extends JpaRepository<ExportRequirementChange, Long> {
    List<ExportRequirementChange> findByStatusOrderByCreatedAtAsc(String status);

    boolean existsByRequirementIdAndStatus(Long requirementId, String status);

    long countByStatus(String status);
}
