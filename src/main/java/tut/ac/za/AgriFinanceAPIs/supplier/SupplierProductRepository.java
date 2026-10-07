package tut.ac.za.AgriFinanceAPIs.supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SupplierProductRepository extends JpaRepository<SupplierProduct,String> {
    List<SupplierProduct> findAllBySupplierId(String supplierId);
}
