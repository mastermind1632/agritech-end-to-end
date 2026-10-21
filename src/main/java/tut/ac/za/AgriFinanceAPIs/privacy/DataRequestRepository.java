package tut.ac.za.AgriFinanceAPIs.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataRequestRepository extends JpaRepository<DataRequest, Long> {
    List<DataRequest> findByStatusOrderByCreatedAtAsc(String status);

    List<DataRequest> findByFarmerIdOrderByCreatedAtDesc(String farmerId);

    boolean existsByFarmerIdAndStatus(String farmerId, String status);
}
