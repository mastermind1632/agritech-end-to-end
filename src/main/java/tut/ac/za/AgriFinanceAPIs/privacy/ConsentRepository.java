package tut.ac.za.AgriFinanceAPIs.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsentRepository extends JpaRepository<ConsentRecord, Long> {

    Optional<ConsentRecord> findFirstByFarmerIdAndPurposeOrderByCreatedAtDescIdDesc(String farmerId, String purpose);

    List<ConsentRecord> findByFarmerIdOrderByCreatedAtDesc(String farmerId);
}
