package tut.ac.za.AgriFinanceAPIs.statements;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BankStatementRepository extends JpaRepository<BankStatement, String> {
    List<BankStatement> findByFarmerIdOrderByCreatedAtDesc(String farmerId);

    long countByStatus(String status);

    long countByFarmerId(String farmerId);
}
