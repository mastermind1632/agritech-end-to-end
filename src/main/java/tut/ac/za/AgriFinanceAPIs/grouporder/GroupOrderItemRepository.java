package tut.ac.za.AgriFinanceAPIs.grouporder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface GroupOrderItemRepository extends JpaRepository<GroupOrderItem,String> {
    List<GroupOrderItem> findAllByGroupOrderIdOrderByJoinedAtAsc(String groupOrderId);
    List<GroupOrderItem> findAllByFarmerId(String farmerId);
    boolean existsByGroupOrderIdAndFarmerId(String groupOrderId,String farmerId);
}
