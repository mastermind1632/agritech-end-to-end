package tut.ac.za.AgriFinanceAPIs.statements;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryMemoryRepository extends JpaRepository<CategoryMemory, Long> {
    List<CategoryMemory> findByFarmerId(String farmerId);

    Optional<CategoryMemory> findByFarmerIdAndMerchantKey(String farmerId, String merchantKey);
}
