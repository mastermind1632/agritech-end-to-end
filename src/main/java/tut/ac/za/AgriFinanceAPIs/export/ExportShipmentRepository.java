package tut.ac.za.AgriFinanceAPIs.export;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExportShipmentRepository extends JpaRepository<ExportShipment, Long> {
    List<ExportShipment> findByFarmerId(String farmerId);
}
