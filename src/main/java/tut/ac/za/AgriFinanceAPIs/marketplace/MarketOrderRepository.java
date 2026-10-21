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

    long countBySellerIdAndStatus(String sellerId, String status);

    long countByStatus(String status);

    /** Rows of [unit, average sold price, count] for completed sales of a product since a date. */
    @Query("select o.unit, avg(o.pricePerUnit), count(o) from MarketOrder o where o.status = 'COMPLETED' "
            + "and lower(o.product) like :pattern and o.completedAt >= :since group by o.unit")
    List<Object[]> soldPrices(@Param("pattern") String pattern, @Param("since") java.time.LocalDateTime since);

    /** Rows of [product, unit, average sold price, count] for completed sales since a date. */
    @Query("select lower(o.product), o.unit, avg(o.pricePerUnit), count(o) from MarketOrder o where o.status = 'COMPLETED' "
            + "and o.completedAt >= :since group by lower(o.product), o.unit")
    List<Object[]> soldBoard(@Param("since") java.time.LocalDateTime since);

    @Query("select sum(o.totalAmount) from MarketOrder o where o.status = 'COMPLETED'")
    java.math.BigDecimal completedValue();

    /** Completed sales to match against bank credits (so a sale is not counted twice). */
    List<MarketOrder> findBySellerIdAndStatusAndCompletedAtAfter(String sellerId, String status, java.time.LocalDateTime after);
}
