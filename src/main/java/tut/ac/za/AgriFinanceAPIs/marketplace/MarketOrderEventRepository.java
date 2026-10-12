package tut.ac.za.AgriFinanceAPIs.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MarketOrderEventRepository extends JpaRepository<MarketOrderEvent, Long> {
    List<MarketOrderEvent> findByOrderIdInOrderByCreatedAtAsc(Collection<Long> orderIds);
}
