package tut.ac.za.AgriFinanceAPIs.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findAllByFarmerIdOrderByDateDesc(String farmerId);

    @Query("select sum(e.amount) from Expense e where e.farmerId = :farmerId")
    BigDecimal sumAmountByFarmerId(@Param("farmerId") String farmerId);

    @Query("select sum(e.amount) from Expense e where e.farmerId = :farmerId and lower(e.crop) = lower(:crop) and e.date >= :since")
    BigDecimal sumForCrop(@Param("farmerId") String farmerId, @Param("crop") String crop, @Param("since") java.time.LocalDate since);

    @Query("select sum(e.amount) from Expense e where e.farmerId = :farmerId and e.crop is null and e.date >= :since")
    BigDecimal sumUntaggedSince(@Param("farmerId") String farmerId, @Param("since") java.time.LocalDate since);

    long countByFarmerId(String farmerId);
}
