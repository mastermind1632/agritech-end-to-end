package tut.ac.za.AgriFinanceAPIs.marketplace;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {
    List<Listing> findBySellerIdAndStatusNotOrderByCreatedAtDesc(String sellerId, String status);

    /** Locks the listing row while stock is reserved or released, so two buyers cannot take the last crate. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l where l.id = :id")
    Optional<Listing> findForUpdate(@Param("id") Long id);

    /** Rows of [unit, average asking price, count] for active listings whose product name matches. */
    @Query("select l.unit, avg(l.pricePerUnit), count(l) from Listing l where l.status = 'ACTIVE' and lower(l.product) like :pattern group by l.unit")
    List<Object[]> askingPrices(@Param("pattern") String pattern);

    /** Rows of [product, unit, average asking price, min, max, count] across all active listings. */
    @Query("select lower(l.product), l.unit, avg(l.pricePerUnit), min(l.pricePerUnit), max(l.pricePerUnit), count(l) "
            + "from Listing l where l.status = 'ACTIVE' group by lower(l.product), l.unit order by count(l) desc")
    List<Object[]> priceBoard();

    long countByStatus(String status);

    long countBySellerIdAndStatus(String sellerId, String status);
}
