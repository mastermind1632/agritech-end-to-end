package tut.ac.za.AgriFinanceAPIs.exportreadiness;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExportRequirementRepository extends JpaRepository<ExportRequirement, Long> {
    List<ExportRequirement> findByProductIgnoreCaseAndDestinationIgnoreCaseOrderByIdAsc(String product, String destination);
    Optional<ExportRequirement> findByCatalogKey(String catalogKey);
    long countByCatalogKey(String catalogKey);
}
