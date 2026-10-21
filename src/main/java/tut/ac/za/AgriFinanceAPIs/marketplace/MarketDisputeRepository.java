package tut.ac.za.AgriFinanceAPIs.marketplace;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MarketDisputeRepository extends JpaRepository<MarketDispute, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from MarketDispute d where d.id = :id")
    Optional<MarketDispute> findForUpdate(@Param("id") Long id);

    @Query("select d from MarketDispute d where d.raisedBy = :user or d.againstId = :user order by d.createdAt desc")
    List<MarketDispute> forUser(@Param("user") String userId);

    List<MarketDispute> findByStatusInOrderByCreatedAtAsc(Collection<String> statuses);

    List<MarketDispute> findAllByOrderByCreatedAtDesc();

    List<MarketDispute> findByOrderIdInOrderByCreatedAtDesc(Collection<Long> orderIds);

    long countByStatusIn(Collection<String> statuses);

    /** Disputes a buyer lost on no-show or non-payment: used in their reliability score. */
    @Query("select count(d) from MarketDispute d where d.againstId = :buyer and d.reason in ('NO_SHOW', 'NON_PAYMENT') "
            + "and d.resolution = 'SELLER_FAVOUR'")
    long lostAsBuyer(@Param("buyer") String buyerId);
}
