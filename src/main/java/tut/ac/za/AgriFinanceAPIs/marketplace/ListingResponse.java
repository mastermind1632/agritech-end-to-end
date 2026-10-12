package tut.ac.za.AgriFinanceAPIs.marketplace;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ListingResponse(
        Long id,
        String sellerId,
        String sellerName,
        String sellerLocation,
        boolean mine,
        String product,
        String category,
        String grade,
        String unit,
        BigDecimal pricePerUnit,      // normal price
        BigDecimal effectivePrice,    // what a buyer pays today (sale price while a sale is on)
        boolean onSale,
        BigDecimal salePrice,
        LocalDate saleEndsOn,
        BigDecimal costPerUnit,       // only filled in for the owner
        BigDecimal quantityAvailable,
        LocalDate availableFrom,
        LocalDate availableUntil,
        String description,
        String location,
        Double latitude,
        Double longitude,
        String status,
        Double distanceKm,
        LocalDateTime createdAt,
        List<Long> imageIds,
        Double sellerRating,
        long sellerRatingCount,
        Double productRating,
        long productRatingCount) { }
