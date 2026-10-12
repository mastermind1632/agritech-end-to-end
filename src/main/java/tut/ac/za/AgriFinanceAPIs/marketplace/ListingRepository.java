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
}
