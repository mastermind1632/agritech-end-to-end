package tut.ac.za.AgriFinanceAPIs.exportreadiness;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExportApplicationRepository extends JpaRepository<ExportApplication, Long> {
    List<ExportApplication> findByFarmerIdOrderByUpdatedAtDesc(String farmerId);
}
