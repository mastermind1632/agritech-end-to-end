package tut.ac.za.AgriFinanceAPIs.companion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanionTaskRepository extends JpaRepository<CompanionTask, Long> {
    List<CompanionTask> findByFarmerId(String farmerId);

    Optional<CompanionTask> findByFarmerIdAndStepCode(String farmerId, String stepCode);
}
