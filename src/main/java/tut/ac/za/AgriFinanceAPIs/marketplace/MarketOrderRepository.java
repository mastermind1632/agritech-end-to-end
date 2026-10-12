package tut.ac.za.AgriFinanceAPIs.marketplace;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MarketOrderRepository extends JpaRepository<MarketOrder, Long> {

    /** Row lock so two requests cannot move the same order at the same moment (e.g. a double click). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from MarketOrder o where o.id = :id")
    Optional<MarketOrder> findForUpdate(@Param("id") Long id);

    List<MarketOrder> findByBuyerIdOrderByCreatedAtDesc(String buyerId);

    List<MarketOrder> findBySellerIdOrderByCreatedAtDesc(String sellerId);

    long countByBuyerIdAndStatus(String buyerId, String status);
}
