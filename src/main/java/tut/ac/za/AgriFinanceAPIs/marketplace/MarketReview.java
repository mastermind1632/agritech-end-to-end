package tut.ac.za.AgriFinanceAPIs.marketplace;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A 1-5 star rating of a FARMER, a PRODUCT (listing) or a SUPPLIER. Farmer/product ratings are tied to a completed order. */
@Entity
@Table(name = "market_reviews", indexes = @Index(name = "idx_market_reviews_target", columnList = "target_type, target_id"), uniqueConstraints = @UniqueConstraint(name = "uk_market_review_once", columnNames = { "reviewer_id", "target_type", "target_id", "order_id" }))
public class MarketReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 36)
    private String targetId;

    @Column(name = "reviewer_id", nullable = false, length = 36)
    private String reviewerId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public MarketReview() { }

    @PrePersist
    void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
