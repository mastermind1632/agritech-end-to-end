package tut.ac.za.AgriFinanceAPIs.exportready;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExportDestinationRepository extends JpaRepository<ExportDestination, String> {
    List<ExportDestination> findByActiveTrueOrderByNameAsc();
}
