package tut.ac.za.AgriFinanceAPIs.vault;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface VaultDocumentRepository extends JpaRepository<VaultDocument, String> {
    List<VaultDocument> findByFarmerIdOrderByUploadedAtDesc(String farmerId);
}
