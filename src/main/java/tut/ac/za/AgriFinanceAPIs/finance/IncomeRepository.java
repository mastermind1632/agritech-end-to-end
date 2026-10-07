package tut.ac.za.AgriFinanceAPIs.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface IncomeRepository extends JpaRepository<Income, Long> {
    List<Income> findAllByFarmerIdOrderByDateDesc(String farmerId);

    @Query("select sum(i.amount) from Income i where i.farmerId = :farmerId")
    BigDecimal sumAmountByFarmerId(@Param("farmerId") String farmerId);
}
