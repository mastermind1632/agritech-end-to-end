package tut.ac.za.AgriFinanceAPIs.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MarketReviewRepository extends JpaRepository<MarketReview, Long> {

    List<MarketReview> findTop20ByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, String targetId);

    boolean existsByOrderIdAndTargetType(Long orderId, String targetType);

    Optional<MarketReview> findByReviewerIdAndTargetTypeAndTargetId(String reviewerId, String targetType, String targetId);

    /** Rows of [targetId, average, count] for many targets at once. */
    @Query("select r.targetId, avg(r.rating), count(r) from MarketReview r where r.targetType = :type and r.targetId in :ids group by r.targetId")
    List<Object[]> aggregate(@Param("type") String type, @Param("ids") Collection<String> ids);

    @Query("select r.orderId from MarketReview r where r.targetType = :type and r.orderId in :ids")
    List<Long> reviewedOrderIds(@Param("ids") Collection<Long> ids, @Param("type") String type);

    /** How many group orders this farmer joined for products of this supplier (proof they bought from them). */
    @Query("select count(i) from GroupOrderItem i, GroupOrder g, SupplierProduct p "
            + "where i.farmerId = :farmerId and g.id = i.groupOrderId and p.id = g.productId and p.supplierId = :supplierId")
    long countGroupPurchases(@Param("farmerId") String farmerId, @Param("supplierId") String supplierId);
}
