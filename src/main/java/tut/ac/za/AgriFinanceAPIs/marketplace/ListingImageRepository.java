package tut.ac.za.AgriFinanceAPIs.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ListingImageRepository extends JpaRepository<ListingImage, Long> {

    long countByListingId(Long listingId);

    /** Returns rows of [imageId, listingId] only, so lists never load the photo bytes. */
    @Query("select i.id, i.listingId from ListingImage i where i.listingId in :ids order by i.id")
    List<Object[]> idsFor(@Param("ids") Collection<Long> ids);
}
