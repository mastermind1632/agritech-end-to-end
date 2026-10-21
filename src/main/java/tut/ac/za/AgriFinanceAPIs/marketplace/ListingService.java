package tut.ac.za.AgriFinanceAPIs.marketplace;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;
import tut.ac.za.AgriFinanceAPIs.companion.BusinessProfileRepository;
import tut.ac.za.AgriFinanceAPIs.exportready.ExportService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ListingService {

    private static final Set<String> UNITS = Set.of("kg", "tonne", "crate", "bag", "bunch", "head", "dozen", "litre");
    private static final Set<String> EDITABLE_STATUSES = Set.of("ACTIVE", "PAUSED");

    private final ListingRepository listings;
    private final FarmerRepository farmers;
    private final ListingImageRepository images;
    private final ReviewService reviewService;
    private final BusinessProfileRepository profiles;
    private final ExportService exports;

    public ListingService(ListingRepository listings, FarmerRepository farmers,
                          ListingImageRepository images, ReviewService reviewService,
                          BusinessProfileRepository profiles, ExportService exports) {
        this.listings = listings;
        this.farmers = farmers;
        this.images = images;
        this.reviewService = reviewService;
        this.profiles = profiles;
        this.exports = exports;
    }

    /** Search filters. Price filters use the price a buyer would actually pay today (sale price included). */
    public record Filters(String q, String category, BigDecimal minPrice, BigDecimal maxPrice,
                          LocalDate availableOn, Double lat, Double lng, Double radiusKm) { }

    /** Sort: newest (default), price_asc, price_desc, rating, distance. minRating filters on the seller's rating. */
    public record Sorting(String sort, Double minRating, boolean verifiedOnly) { }

    private record Ctx(Map<String, Farmer> sellers, Map<Long, List<Long>> images,
                       Map<String, ReviewService.Agg> farmerRatings, Map<String, ReviewService.Agg> productRatings,
                       Set<String> verified, Set<String> exportReady) { }

    /** Search, then filter on rating / verification and sort. Paging is applied by the caller. */
    @Transactional(readOnly = true)
    public List<ListingResponse> search(Filters f, Sorting s, String viewerId) {
        List<ListingResponse> all = new ArrayList<>(search(f, viewerId));
        if (s == null) return all;
        if (s.minRating() != null) all.removeIf(l -> l.sellerRating() == null || l.sellerRating() < s.minRating());
        if (s.verifiedOnly()) all.removeIf(l -> !l.sellerVerified());
        String sort = s.sort() == null ? "newest" : s.sort().trim().toLowerCase();
        Comparator<ListingResponse> cmp = switch (sort) {
            case "price_asc" -> Comparator.comparing(ListingResponse::effectivePrice);
            case "price_desc" -> Comparator.comparing(ListingResponse::effectivePrice).reversed();
            case "rating" -> Comparator.comparing((ListingResponse l) -> l.sellerRating() == null ? 0.0 : l.sellerRating()).reversed()
                    .thenComparing(ListingResponse::sellerRatingCount, Comparator.reverseOrder());
            case "distance" -> Comparator.comparing(ListingResponse::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> null;
        };
        if (cmp != null) all.sort(cmp);
        return all;
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> search(Filters f, String viewerId) {
        LocalDate today = LocalDate.now();

        Specification<Listing> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("status"), "ACTIVE"));
            ps.add(cb.greaterThan(root.<BigDecimal>get("quantityAvailable"), BigDecimal.ZERO));
            ps.add(cb.or(cb.isNull(root.get("availableUntil")),
                    cb.greaterThanOrEqualTo(root.<LocalDate>get("availableUntil"), today)));
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.<String>get("product")), like),
                        cb.like(cb.lower(root.<String>get("description")), like)));
            }
            if (f.category() != null && !f.category().isBlank()) {
                ps.add(cb.equal(root.get("category"), f.category().trim()));
            }
            if (f.availableOn() != null) {
                ps.add(cb.or(cb.isNull(root.get("availableFrom")),
                        cb.lessThanOrEqualTo(root.<LocalDate>get("availableFrom"), f.availableOn())));
                ps.add(cb.or(cb.isNull(root.get("availableUntil")),
                        cb.greaterThanOrEqualTo(root.<LocalDate>get("availableUntil"), f.availableOn())));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };

        List<Listing> found = listings.findAll(spec,
                PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();

        List<Listing> kept = new ArrayList<>();
        for (Listing l : found) {
            BigDecimal price = l.effectivePrice(today);
            if (f.minPrice() != null && price.compareTo(f.minPrice()) < 0) continue;
            if (f.maxPrice() != null && price.compareTo(f.maxPrice()) > 0) continue;
            kept.add(l);
        }

        Ctx ctx = context(kept);
        boolean byDistance = f.lat() != null && f.lng() != null;
        List<ListingResponse> out = new ArrayList<>();
        for (Listing l : kept) {
            Double distance = null;
            if (byDistance && l.getLatitude() != null && l.getLongitude() != null) {
                distance = haversineKm(f.lat(), f.lng(), l.getLatitude(), l.getLongitude());
            }
            if (byDistance && f.radiusKm() != null && (distance == null || distance > f.radiusKm())) continue;
            out.add(toResponse(l, ctx, viewerId, distance));
        }
        if (byDistance) {
            out.sort(Comparator.comparing(ListingResponse::distanceKm, Comparator.nullsLast(Comparator.naturalOrder())));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ListingResponse> mine(String viewerId) {
        List<Listing> own = listings.findBySellerIdAndStatusNotOrderByCreatedAtDesc(viewerId, "REMOVED");
        Ctx ctx = context(own);
        return own.stream().map(l -> toResponse(l, ctx, viewerId, null)).toList();
    }

    @Transactional(readOnly = true)
    public ListingResponse get(Long id, String viewerId) {
        Listing l = find(id);
        if ("REMOVED".equals(l.getStatus())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found");
        return toResponse(l, context(List.of(l)), viewerId, null);
    }

    @Transactional
    public ListingResponse create(String sellerId, ListingRequest r) {
        validate(r);
        Listing l = new Listing();
        l.setSellerId(sellerId);
        apply(l, r);
        Listing saved = listings.save(l);
        return toResponse(saved, context(List.of(saved)), sellerId, null);
    }

    @Transactional
    public ListingResponse update(Long id, ListingRequest r, String viewerId, boolean admin) {
        Listing l = find(id);
        requireOwnerOrAdmin(l, viewerId, admin);
        if ("REMOVED".equals(l.getStatus())) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found");
        validate(r);
        apply(l, r);
        Listing saved = listings.save(l);
        return toResponse(saved, context(List.of(saved)), viewerId, null);
    }

    @Transactional
    public void remove(Long id, String viewerId, boolean admin) {
        Listing l = find(id);
        requireOwnerOrAdmin(l, viewerId, admin);
        l.setStatus("REMOVED");
        listings.save(l);
    }

    // ---- helpers ----

    private Listing find(Long id) {
        return listings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
    }

    private void requireOwnerOrAdmin(Listing l, String viewerId, boolean admin) {
        if (!admin && !l.getSellerId().equals(viewerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only change your own listings");
        }
    }

    private void validate(ListingRequest r) {
        if (r == null) throw bad("Listing details are required");
        if (r.product() == null || r.product().isBlank()) throw bad("Product name is required");
        if (r.product().trim().length() > 100) throw bad("Product name is too long (max 100 characters)");
        if (r.unit() == null || !UNITS.contains(r.unit().trim().toLowerCase())) throw bad("Unit must be one of: " + String.join(", ", new TreeSet<>(UNITS)));
        if (r.pricePerUnit() == null || r.pricePerUnit().signum() <= 0) throw bad("Price per unit must be greater than zero");
        if (r.quantityAvailable() == null || r.quantityAvailable().signum() < 0) throw bad("Quantity must be zero or more");
        if (r.availableFrom() != null && r.availableUntil() != null && r.availableUntil().isBefore(r.availableFrom())) {
            throw bad("'Available until' cannot be before 'available from'");
        }
        if (r.latitude() != null && (r.latitude() < -90 || r.latitude() > 90)) throw bad("Latitude must be between -90 and 90");
        if (r.longitude() != null && (r.longitude() < -180 || r.longitude() > 180)) throw bad("Longitude must be between -180 and 180");
        if ((r.latitude() == null) != (r.longitude() == null)) throw bad("Provide both latitude and longitude, or neither");
        if (r.description() != null && r.description().length() > 1000) throw bad("Description is too long (max 1000 characters)");
        if (r.status() != null && !r.status().isBlank() && !EDITABLE_STATUSES.contains(r.status().trim().toUpperCase())) {
            throw bad("Status can only be ACTIVE or PAUSED");
        }
        if (r.costPerUnit() != null && r.costPerUnit().signum() < 0) throw bad("Cost per unit cannot be negative");
        if (r.salePrice() != null) {
            if (r.salePrice().signum() <= 0) throw bad("Sale price must be greater than zero");
            if (r.salePrice().compareTo(r.pricePerUnit()) >= 0) throw bad("Sale price must be lower than the normal price");
            if (r.saleEndsOn() != null && r.saleEndsOn().isBefore(LocalDate.now())) throw bad("The sale end date cannot be in the past");
        }
    }

    private void apply(Listing l, ListingRequest r) {
        l.setProduct(r.product().trim());
        l.setCategory(blankToNull(r.category()));
        l.setGrade(blankToNull(r.grade()));
        l.setUnit(r.unit().trim().toLowerCase());
        l.setPricePerUnit(r.pricePerUnit());
        l.setQuantityAvailable(r.quantityAvailable());
        l.setAvailableFrom(r.availableFrom());
        l.setAvailableUntil(r.availableUntil());
        l.setDescription(blankToNull(r.description()));
        l.setLocation(blankToNull(r.location()));
        l.setLatitude(r.latitude());
        l.setLongitude(r.longitude());
        l.setCostPerUnit(r.costPerUnit());
        l.setSalePrice(r.salePrice());
        l.setSaleEndsOn(r.salePrice() == null ? null : r.saleEndsOn());

        String requested = (r.status() == null || r.status().isBlank()) ? null : r.status().trim().toUpperCase();
        if (r.quantityAvailable().signum() == 0) {
            l.setStatus("SOLD_OUT");
        } else if (requested != null) {
            l.setStatus(requested);
        } else if (l.getStatus() == null || "SOLD_OUT".equals(l.getStatus())) {
            l.setStatus("ACTIVE");
        }
    }

    /** Loads sellers, photo ids and ratings for a whole list with a handful of queries (no per-listing queries). */
    private Ctx context(List<Listing> ls) {
        Set<String> sellerIds = ls.stream().map(Listing::getSellerId).collect(Collectors.toSet());
        Set<Long> listingIds = ls.stream().map(Listing::getId).collect(Collectors.toSet());
        Map<String, Farmer> sellers = sellerIds.isEmpty() ? Map.of()
                : farmers.findAllById(sellerIds).stream().collect(Collectors.toMap(Farmer::getId, x -> x));
        Map<Long, List<Long>> imgs = new HashMap<>();
        if (!listingIds.isEmpty()) {
            for (Object[] row : images.idsFor(listingIds)) {
                imgs.computeIfAbsent((Long) row[1], k -> new ArrayList<>()).add((Long) row[0]);
            }
        }
        Map<String, ReviewService.Agg> fr = reviewService.aggregates("FARMER", sellerIds);
        Map<String, ReviewService.Agg> pr = reviewService.aggregates("PRODUCT",
                listingIds.stream().map(String::valueOf).collect(Collectors.toSet()));
        Set<String> verified = sellerIds.isEmpty() ? Set.of() : new HashSet<>(profiles.verifiedAmong(sellerIds));
        Set<String> exportReady = exports.exportReady(sellerIds);
        return new Ctx(sellers, imgs, fr, pr, verified, exportReady);
    }

    private ListingResponse toResponse(Listing l, Ctx ctx, String viewerId, Double distanceKm) {
        LocalDate today = LocalDate.now();
        Farmer s = ctx.sellers().get(l.getSellerId());
        boolean mine = l.getSellerId().equals(viewerId);
        boolean onSale = l.isOnSale(today);
        ReviewService.Agg fr = ctx.farmerRatings().get(l.getSellerId());
        ReviewService.Agg pr = ctx.productRatings().get(String.valueOf(l.getId()));
        return new ListingResponse(l.getId(), l.getSellerId(),
                s != null ? s.getName() : "Farmer", s != null ? s.getLocation() : null, mine,
                l.getProduct(), l.getCategory(), l.getGrade(), l.getUnit(),
                l.getPricePerUnit(), l.effectivePrice(today), onSale,
                (mine || onSale) ? l.getSalePrice() : null, (mine || onSale) ? l.getSaleEndsOn() : null,
                mine ? l.getCostPerUnit() : null,
                l.getQuantityAvailable(), l.getAvailableFrom(), l.getAvailableUntil(), l.getDescription(),
                l.getLocation(), l.getLatitude(), l.getLongitude(), l.getStatus(),
                distanceKm == null ? null : Math.round(distanceKm * 10.0) / 10.0, l.getCreatedAt(),
                ctx.images().getOrDefault(l.getId(), List.of()),
                fr == null ? null : fr.average(), fr == null ? 0 : fr.count(),
                pr == null ? null : pr.average(), pr == null ? 0 : pr.count(),
                ctx.verified().contains(l.getSellerId()), ctx.exportReady().contains(l.getSellerId()));
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
