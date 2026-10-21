package tut.ac.za.AgriFinanceAPIs.exportready;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExportProductRepository extends JpaRepository<ExportProduct, String> {
    List<ExportProduct> findByActiveTrueOrderByNameAsc();
}
