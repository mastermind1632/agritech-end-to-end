package tut.ac.za.AgriFinanceAPIs.companion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, String> {

    @Query("select p.farmerId from BusinessProfile p where p.verified = true and p.farmerId in :ids")
    List<String> verifiedAmong(@org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);

    /** Profiles with a registration number that an admin has not yet verified: the verification queue. */
    @Query("select p from BusinessProfile p where p.verified = false and p.registrationNumber is not null order by p.updatedAt asc")
    List<BusinessProfile> awaitingVerification();

    long countByVerifiedTrue();

    long countByIntakeCompletedAtIsNotNull();

    List<BusinessProfile> findByFarmerIdIn(Collection<String> ids);
}
