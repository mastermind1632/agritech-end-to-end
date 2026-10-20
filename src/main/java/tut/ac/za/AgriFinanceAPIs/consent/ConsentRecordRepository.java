package tut.ac.za.AgriFinanceAPIs.consent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {
    List<ConsentRecord> findByFarmerIdOrderByGrantedAtDesc(String farmerId);
    Optional<ConsentRecord> findFirstByFarmerIdAndConsentTypeOrderByGrantedAtDesc(String farmerId, String consentType);
}
