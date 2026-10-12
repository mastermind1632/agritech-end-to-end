package tut.ac.za.AgriFinanceAPIs.marketplace;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Body for creating or updating a listing. The seller is never taken from the body, only from the login token. */
public record ListingRequest(
        String product,
        String category,
        String grade,
        String unit,
        BigDecimal pricePerUnit,
        BigDecimal quantityAvailable,
        LocalDate availableFrom,
        LocalDate availableUntil,
        String description,
        String location,
        Double latitude,
        Double longitude,
        String status,
        BigDecimal costPerUnit,
        BigDecimal salePrice,
        LocalDate saleEndsOn) { }
