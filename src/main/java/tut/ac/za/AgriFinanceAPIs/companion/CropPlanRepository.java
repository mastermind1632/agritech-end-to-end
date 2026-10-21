package tut.ac.za.AgriFinanceAPIs.companion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CropPlanRepository extends JpaRepository<CropPlan, Long> {
    List<CropPlan> findByFarmerIdOrderByCropAsc(String farmerId);
}
