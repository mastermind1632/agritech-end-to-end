package tut.ac.za.AgriFinanceAPIs.supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SupplierRepository extends JpaRepository<Supplier,String> {
    List<Supplier> findByOwnerId(String ownerId);
}
