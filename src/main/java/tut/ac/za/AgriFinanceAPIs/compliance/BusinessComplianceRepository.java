package tut.ac.za.AgriFinanceAPIs.compliance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BusinessComplianceRepository extends JpaRepository<BusinessCompliance, Long> {
    Optional<BusinessCompliance> findByFarmerId(String farmerId);
}
