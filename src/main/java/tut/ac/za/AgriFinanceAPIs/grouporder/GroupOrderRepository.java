package tut.ac.za.AgriFinanceAPIs.grouporder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
public interface GroupOrderRepository extends JpaRepository<GroupOrder,String>{
    List<GroupOrder> findAllByStatusOrderByCreatedAtDesc(String status);

    /** Row lock: two farmers joining at the same moment can no longer push the order past its target. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GroupOrder g where g.id = :id")
    Optional<GroupOrder> findForUpdate(@Param("id") String id);

    boolean existsByProductId(String productId);

    List<GroupOrder> findByStatusAndDeadlineBefore(String status, LocalDateTime time);

    long countByStatus(String status);
}
