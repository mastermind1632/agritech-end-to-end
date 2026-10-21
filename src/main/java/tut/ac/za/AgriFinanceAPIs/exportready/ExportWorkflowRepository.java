package tut.ac.za.AgriFinanceAPIs.exportready;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ExportWorkflowRepository extends JpaRepository<ExportWorkflow, String> {
    List<ExportWorkflow> findByFarmerIdOrderByCreatedAtDesc(String farmerId);

    List<ExportWorkflow> findByStatusAndProductCodeAndDestinationCode(String status, String product, String destination);

    List<ExportWorkflow> findByStatus(String status);

    List<ExportWorkflow> findByFarmerIdInAndStatusIn(Collection<String> farmerIds, Collection<String> statuses);

    long countByStatus(String status);
}
