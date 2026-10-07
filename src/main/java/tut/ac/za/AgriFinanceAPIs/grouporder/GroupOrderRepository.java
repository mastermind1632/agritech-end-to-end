package tut.ac.za.AgriFinanceAPIs.grouporder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface GroupOrderRepository extends JpaRepository<GroupOrder,String>{List<GroupOrder> findAllByStatusOrderByCreatedAtDesc(String status);}
