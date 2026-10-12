package tut.ac.za.AgriFinanceAPIs.marketplace;

import java.time.LocalDateTime;
import java.util.List;

public final class ReviewDtos {
    private ReviewDtos() { }

    public record OrderReview(Integer farmerRating, Integer productRating, String comment) { }

    public record SupplierReview(Integer rating, String comment) { }

    public record ReviewView(int rating, String comment, String reviewerName, LocalDateTime at) { }

    /** canRate is only filled in for suppliers: true when the viewer has joined a group order with them. */
    public record ReviewSummary(Double average, long count, List<ReviewView> reviews, Boolean canRate) { }

    public record RatingView(Double average, long count) { }
}
