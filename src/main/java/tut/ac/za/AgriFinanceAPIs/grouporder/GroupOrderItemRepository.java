package tut.ac.za.AgriFinanceAPIs.grouporder;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface GroupOrderItemRepository extends JpaRepository<GroupOrderItem,String>{List<GroupOrderItem> findAllByGroupOrderIdOrderByJoinedAtAsc(String groupOrderId); boolean existsByGroupOrderIdAndFarmerId(String groupOrderId,String farmerId);}
