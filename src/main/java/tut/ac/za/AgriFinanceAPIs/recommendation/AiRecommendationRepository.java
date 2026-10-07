package tut.ac.za.AgriFinanceAPIs.recommendation;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface AiRecommendationRepository extends JpaRepository<AiRecommendation,String>{List<AiRecommendation> findAllByFarmerIdOrderByCreatedAtDesc(String farmerId);}
