package tut.ac.za.AgriFinanceAPIs.statements;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AllocationRuleRepository extends JpaRepository<AllocationRule, Long> {
    List<AllocationRule> findByFarmerId(String farmerId);

    Optional<AllocationRule> findByFarmerIdAndCategory(String farmerId, String category);
}
