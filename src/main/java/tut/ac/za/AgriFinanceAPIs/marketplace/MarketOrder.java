package tut.ac.za.AgriFinanceAPIs.marketplace;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One order = one listing line. Status flow: PENDING, CONFIRMED, DISPATCHED, DELIVERED, COMPLETED (or DECLINED / CANCELLED). */
@Entity
@Table(name = "market_orders", indexes = { @Index(name = "idx_market_orders_buyer", columnList = "buyer_id"), @Index(name = "idx_market_orders_seller", columnList = "seller_id") })
public class MarketOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "buyer_id", nullable = false, length = 36)
    private String buyerId;

    @Column(name = "seller_id", nullable = false, length = 36)
    private String sellerId;

    @Column(nullable = false, length = 100)
    private String product;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "price_per_unit", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerUnit;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "cost_total", precision = 12, scale = 2)
    private BigDecimal costTotal;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "delivery_note", length = 500)
    private String deliveryNote;

    @Column(name = "delivery_code", length = 6)
    private String deliveryCode;

    @Column(name = "code_attempts", nullable = false)
    private int codeAttempts;

    @Column(name = "stock_reserved", nullable = false)
    private boolean stockReserved;

    @Column(name = "buyer_confirmed_paid", nullable = false)
    private boolean buyerConfirmedPaid;

    @Column(name = "seller_confirmed_paid", nullable = false)
    private boolean sellerConfirmedPaid;

    @Column(name = "delivered_lat")
    private Double deliveredLat;

    @Column(name = "delivered_lng")
    private Double deliveredLng;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "dispatched_at")
    private LocalDateTime dispatchedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public MarketOrder() { }

    @PrePersist
    void onCreate() { LocalDateTime now = LocalDateTime.now(); createdAt = now; updatedAt = now; }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getListingId() { return listingId; }
    public void setListingId(Long listingId) { this.listingId = listingId; }
    public String getBuyerId() { return buyerId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public String getProduct() { return product; }
    public void setProduct(String product) { this.product = product; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getCostTotal() { return costTotal; }
    public void setCostTotal(BigDecimal costTotal) { this.costTotal = costTotal; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDeliveryNote() { return deliveryNote; }
    public void setDeliveryNote(String deliveryNote) { this.deliveryNote = deliveryNote; }
    public String getDeliveryCode() { return deliveryCode; }
    public void setDeliveryCode(String deliveryCode) { this.deliveryCode = deliveryCode; }
    public int getCodeAttempts() { return codeAttempts; }
    public void setCodeAttempts(int codeAttempts) { this.codeAttempts = codeAttempts; }
    public boolean isStockReserved() { return stockReserved; }
    public void setStockReserved(boolean stockReserved) { this.stockReserved = stockReserved; }
    public boolean isBuyerConfirmedPaid() { return buyerConfirmedPaid; }
    public void setBuyerConfirmedPaid(boolean buyerConfirmedPaid) { this.buyerConfirmedPaid = buyerConfirmedPaid; }
    public boolean isSellerConfirmedPaid() { return sellerConfirmedPaid; }
    public void setSellerConfirmedPaid(boolean sellerConfirmedPaid) { this.sellerConfirmedPaid = sellerConfirmedPaid; }
    public Double getDeliveredLat() { return deliveredLat; }
    public void setDeliveredLat(Double deliveredLat) { this.deliveredLat = deliveredLat; }
    public Double getDeliveredLng() { return deliveredLng; }
    public void setDeliveredLng(Double deliveredLng) { this.deliveredLng = deliveredLng; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public LocalDateTime getDispatchedAt() { return dispatchedAt; }
    public void setDispatchedAt(LocalDateTime dispatchedAt) { this.dispatchedAt = dispatchedAt; }
    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
