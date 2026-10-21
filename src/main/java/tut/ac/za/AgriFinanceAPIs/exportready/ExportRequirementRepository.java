package tut.ac.za.AgriFinanceAPIs.exportready;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExportRequirementRepository extends JpaRepository<ExportRequirement, Long> {

    /** The checklist query: everything for this product and destination, plus items that apply to all. */
    @Query("select r from ExportRequirement r where r.active = true "
            + "and (r.productCode is null or r.productCode = :product) "
            + "and (r.destinationCode is null or r.destinationCode = :destination) order by r.stage, r.id")
    List<ExportRequirement> forPair(@Param("product") String product, @Param("destination") String destination);

    List<ExportRequirement> findAllByOrderByStageAscIdAsc();

    List<ExportRequirement> findByActiveTrueAndSourceUrlIsNotNull();
}
