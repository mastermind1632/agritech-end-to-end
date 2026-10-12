package tut.ac.za.AgriFinanceAPIs.marketplace;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;
import tut.ac.za.AgriFinanceAPIs.finance.Income;
import tut.ac.za.AgriFinanceAPIs.finance.IncomeRepository;
import tut.ac.za.AgriFinanceAPIs.marketplace.OrderDtos.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Order lifecycle as a small state machine. Only the transitions below are possible, every one is logged,
 * and stock is reserved (deducted from the listing) when the farmer accepts and released if the order is cancelled.
 *
 *   PENDING    -> CONFIRMED (seller) | DECLINED (seller) | CANCELLED (buyer)
 *   CONFIRMED  -> DISPATCHED (seller) | CANCELLED (buyer or seller, stock released)
 *   DISPATCHED -> DELIVERED (seller enters the buyer's one-time code)
 *   DELIVERED  -> COMPLETED (buyer AND seller both confirm the cash; sale is recorded as income)
 */
@Service
public class OrderService {

    private static final int MAX_PENDING_PER_BUYER = 5;
    private static final BigDecimal NEW_BUYER_LIMIT = new BigDecimal("5000.00");
    private static final int MAX_CODE_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MarketOrderRepository orders;
    private final MarketOrderEventRepository events;
    private final ListingRepository listings;
    private final FarmerRepository farmers;
    private final IncomeRepository incomes;
    private final ListingImageRepository images;
    private final MarketReviewRepository reviews;

    public OrderService(MarketOrderRepository orders, MarketOrderEventRepository events, ListingRepository listings,
                        FarmerRepository farmers, IncomeRepository incomes,
                        ListingImageRepository images, MarketReviewRepository reviews) {
        this.orders = orders;
        this.events = events;
        this.listings = listings;
        this.farmers = farmers;
        this.incomes = incomes;
        this.images = images;
        this.reviews = reviews;
    }

    // ---------- buyer places an order ----------

    @Transactional
    public OrderView place(String buyerId, PlaceOrder req) {
        if (req == null || req.listingId() == null) throw bad("Choose a listing to order from");
        if (req.quantity() == null || req.quantity().signum() <= 0) throw bad("Quantity must be greater than zero");
        if (req.note() != null && req.note().length() > 500) throw bad("Delivery note is too long (max 500 characters)");

        Listing l = listings.findById(req.listingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (!"ACTIVE".equals(l.getStatus())) throw bad("This listing is not available right now");
        if (l.getSellerId().equals(buyerId)) throw bad("You cannot order your own produce");
        if (l.getAvailableUntil() != null && l.getAvailableUntil().isBefore(LocalDate.now())) throw bad("This listing has expired");

        BigDecimal qty = req.quantity().setScale(2, RoundingMode.HALF_UP);
        if (qty.signum() <= 0) throw bad("Quantity must be greater than zero");
        if (qty.compareTo(l.getQuantityAvailable()) > 0) {
            throw bad("Only " + l.getQuantityAvailable().stripTrailingZeros().toPlainString() + " " + l.getUnit() + " available");
        }
        // The buyer pays today's price, so a running sale is honoured and then frozen on the order.
        BigDecimal unitPrice = l.effectivePrice(LocalDate.now());
        BigDecimal total = unitPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP);
        BigDecimal costTotal = l.getCostPerUnit() == null ? null : l.getCostPerUnit().multiply(qty).setScale(2, RoundingMode.HALF_UP);

        // Cash on delivery invites fake orders, so new buyers get limits until they complete a first order.
        if (orders.countByBuyerIdAndStatus(buyerId, "COMPLETED") == 0 && total.compareTo(NEW_BUYER_LIMIT) > 0) {
            throw bad("Until you complete your first order, orders are limited to R" + NEW_BUYER_LIMIT.toPlainString());
        }
        if (orders.countByBuyerIdAndStatus(buyerId, "PENDING") >= MAX_PENDING_PER_BUYER) {
            throw bad("You already have " + MAX_PENDING_PER_BUYER + " orders waiting for a farmer to respond");
        }

        MarketOrder o = new MarketOrder();
        o.setListingId(l.getId());
        o.setBuyerId(buyerId);
        o.setSellerId(l.getSellerId());
        o.setProduct(l.getProduct());
        o.setUnit(l.getUnit());
        o.setPricePerUnit(unitPrice);
        o.setQuantity(qty);
        o.setTotalAmount(total);
        o.setCostTotal(costTotal);
        o.setStatus("PENDING");
        o.setDeliveryNote(blankToNull(req.note()));
        orders.save(o);
        log(o, buyerId, null, "PENDING", "Order placed");
        return view(o, buyerId, false);
    }

    // ---------- seller actions ----------

    @Transactional
    public OrderView confirm(Long id, String actor) {
        MarketOrder o = lock(id);
        requireSeller(o, actor);
        requireStatus(o, "PENDING");

        Listing l = listings.findForUpdate(o.getListingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found"));
        if (l.getQuantityAvailable().compareTo(o.getQuantity()) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Not enough stock left. Only " + l.getQuantityAvailable().stripTrailingZeros().toPlainString() + " " + l.getUnit() + " remain");
        }
        l.setQuantityAvailable(l.getQuantityAvailable().subtract(o.getQuantity()));
        if (l.getQuantityAvailable().signum() == 0) l.setStatus("SOLD_OUT");
        listings.save(l);

        o.setStockReserved(true);
        o.setDeliveryCode(String.format("%06d", RANDOM.nextInt(1_000_000)));
        o.setConfirmedAt(LocalDateTime.now());
        move(o, "CONFIRMED", actor, "Farmer accepted the order; stock reserved");
        return view(o, actor, false);
    }

    @Transactional
    public OrderView decline(Long id, String actor, String reason) {
        MarketOrder o = lock(id);
        requireSeller(o, actor);
        requireStatus(o, "PENDING");
        move(o, "DECLINED", actor, noteOf("Farmer declined", reason));
        return view(o, actor, false);
    }

    @Transactional
    public OrderView dispatch(Long id, String actor) {
        MarketOrder o = lock(id);
        requireSeller(o, actor);
        requireStatus(o, "CONFIRMED");
        o.setDispatchedAt(LocalDateTime.now());
        move(o, "DISPATCHED", actor, "Produce is on its way");
        return view(o, actor, false);
    }

    /** noRollbackFor: a wrong code must still be counted, so the attempt counter is saved even though we throw. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public OrderView deliver(Long id, String actor, Deliver req) {
        MarketOrder o = lock(id);
        requireSeller(o, actor);
        requireStatus(o, "DISPATCHED");
        if (req == null || req.code() == null || req.code().isBlank()) throw bad("Enter the 6-digit delivery code from the buyer");
        if (o.getCodeAttempts() >= MAX_CODE_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "Too many wrong codes. This delivery is locked, please contact support");
        }
        boolean ok = MessageDigest.isEqual(
                req.code().trim().getBytes(StandardCharsets.UTF_8), o.getDeliveryCode().getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            o.setCodeAttempts(o.getCodeAttempts() + 1);
            orders.save(o);
            throw bad("Wrong delivery code (" + (MAX_CODE_ATTEMPTS - o.getCodeAttempts()) + " attempts left)");
        }
        o.setDeliveredAt(LocalDateTime.now());
        o.setDeliveredLat(req.latitude());
        o.setDeliveredLng(req.longitude());
        move(o, "DELIVERED", actor, "Delivery confirmed with the buyer's code");
        return view(o, actor, false);
    }

    // ---------- either side ----------

    @Transactional
    public OrderView cancel(Long id, String actor, String reason) {
        MarketOrder o = lock(id);
        requireParticipant(o, actor);
        boolean buyer = actor.equals(o.getBuyerId());
        String s = o.getStatus();
        boolean allowed = buyer ? (s.equals("PENDING") || s.equals("CONFIRMED")) : s.equals("CONFIRMED");
        if (!allowed) throw conflict("This order can no longer be cancelled from its current status (" + s + ")");

        releaseStock(o);
        move(o, "CANCELLED", actor, noteOf(buyer ? "Buyer cancelled" : "Farmer cancelled", reason));
        return view(o, actor, false);
    }

    @Transactional
    public OrderView confirmPayment(Long id, String actor) {
        MarketOrder o = lock(id);
        requireParticipant(o, actor);
        requireStatus(o, "DELIVERED");
        boolean buyer = actor.equals(o.getBuyerId());

        if (buyer ? o.isBuyerConfirmedPaid() : o.isSellerConfirmedPaid()) return view(o, actor, false); // already done
        if (buyer) o.setBuyerConfirmedPaid(true); else o.setSellerConfirmedPaid(true);

        if (o.isBuyerConfirmedPaid() && o.isSellerConfirmedPaid()) {
            o.setCompletedAt(LocalDateTime.now());
            move(o, "COMPLETED", actor, "Both sides confirmed the cash payment");

            Income income = new Income();
            income.setFarmerId(o.getSellerId());
            String item = "Farm Market sale: " + o.getQuantity().stripTrailingZeros().toPlainString() + " " + o.getUnit() + " " + o.getProduct();
            income.setItem(item.length() > 150 ? item.substring(0, 150) : item);
            income.setAmount(o.getTotalAmount());
            income.setDate(LocalDate.now());
            incomes.save(income);
        } else {
            orders.save(o);
            log(o, actor, "DELIVERED", "DELIVERED", buyer ? "Buyer confirmed cash paid" : "Farmer confirmed cash received");
        }
        return view(o, actor, false);
    }

    // ---------- reading ----------

    @Transactional(readOnly = true)
    public List<OrderView> buying(String viewer) {
        return viewList(orders.findByBuyerIdOrderByCreatedAtDesc(viewer), viewer);
    }

    @Transactional(readOnly = true)
    public List<OrderView> selling(String viewer) {
        return viewList(orders.findBySellerIdOrderByCreatedAtDesc(viewer), viewer);
    }

    @Transactional(readOnly = true)
    public OrderView get(Long id, String viewer, boolean admin) {
        MarketOrder o = orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        // Outsiders get 404, not 403, so order numbers cannot be probed.
        if (!admin && !viewer.equals(o.getBuyerId()) && !viewer.equals(o.getSellerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return view(o, viewer, true);
    }

    // ---------- internals ----------

    private MarketOrder lock(Long id) {
        return orders.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    private void requireSeller(MarketOrder o, String actor) {
        if (!actor.equals(o.getSellerId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the selling farmer can do this");
    }

    private void requireParticipant(MarketOrder o, String actor) {
        if (!actor.equals(o.getSellerId()) && !actor.equals(o.getBuyerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
    }

    private void requireStatus(MarketOrder o, String expected) {
        if (!expected.equals(o.getStatus())) {
            throw conflict("This order is " + o.getStatus() + ", so that action is not available");
        }
    }

    private void releaseStock(MarketOrder o) {
        if (!o.isStockReserved()) return;
        listings.findForUpdate(o.getListingId()).ifPresent(l -> {
            l.setQuantityAvailable(l.getQuantityAvailable().add(o.getQuantity()));
            if ("SOLD_OUT".equals(l.getStatus()) && l.getQuantityAvailable().signum() > 0) l.setStatus("ACTIVE");
            listings.save(l);
        });
        o.setStockReserved(false);
    }

    private void move(MarketOrder o, String to, String actor, String note) {
        String from = o.getStatus();
        o.setStatus(to);
        orders.save(o);
        log(o, actor, from, to, note);
    }

    private void log(MarketOrder o, String actor, String from, String to, String note) {
        MarketOrderEvent e = new MarketOrderEvent();
        e.setOrderId(o.getId());
        e.setActorId(actor);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setNote(note != null && note.length() > 300 ? note.substring(0, 300) : note);
        events.save(e);
    }

    private List<OrderView> viewList(List<MarketOrder> list, String viewer) {
        if (list.isEmpty()) return List.of();
        List<Long> orderIds = list.stream().map(MarketOrder::getId).toList();
        Map<Long, List<MarketOrderEvent>> byOrder = events.findByOrderIdInOrderByCreatedAtAsc(orderIds)
                .stream().collect(Collectors.groupingBy(MarketOrderEvent::getOrderId));
        Set<String> ids = new HashSet<>();
        list.forEach(o -> { ids.add(o.getBuyerId()); ids.add(o.getSellerId()); });
        byOrder.values().forEach(es -> es.forEach(e -> { if (e.getActorId() != null) ids.add(e.getActorId()); }));
        Map<String, Farmer> people = farmers.findAllById(ids).stream().collect(Collectors.toMap(Farmer::getId, f -> f));

        Map<Long, Long> thumbs = new HashMap<>();
        for (Object[] row : images.idsFor(list.stream().map(MarketOrder::getListingId).collect(Collectors.toSet()))) {
            thumbs.putIfAbsent((Long) row[1], (Long) row[0]);
        }
        Set<Long> reviewed = new HashSet<>(reviews.reviewedOrderIds(orderIds, "FARMER"));

        return list.stream().map(o -> build(o, viewer, people, byOrder.getOrDefault(o.getId(), List.of()),
                thumbs.get(o.getListingId()), reviewed.contains(o.getId()))).toList();
    }

    private OrderView view(MarketOrder o, String viewer, boolean withEvents) {
        return viewList(List.of(o), viewer).get(0);
    }

    @Transactional(readOnly = true)
    public SalesSummary salesSummary(String sellerId) {
        long completed = 0, pending = 0, missingCost = 0;
        BigDecimal revenue = BigDecimal.ZERO, cost = BigDecimal.ZERO;
        for (MarketOrder o : orders.findBySellerIdOrderByCreatedAtDesc(sellerId)) {
            if ("COMPLETED".equals(o.getStatus())) {
                completed++;
                revenue = revenue.add(o.getTotalAmount());
                if (o.getCostTotal() != null) cost = cost.add(o.getCostTotal()); else missingCost++;
            } else if ("PENDING".equals(o.getStatus())) {
                pending++;
            }
        }
        return new SalesSummary(completed, pending, revenue, cost, revenue.subtract(cost), missingCost);
    }

    private OrderView build(MarketOrder o, String viewer, Map<String, Farmer> people, List<MarketOrderEvent> es,
                            Long imageId, boolean reviewed) {
        boolean isBuyer = viewer.equals(o.getBuyerId());
        boolean isSeller = viewer.equals(o.getSellerId());
        String role = isBuyer ? "BUYER" : isSeller ? "SELLER" : "ADMIN";
        String s = o.getStatus();

        // Phone numbers are only shared once the farmer has accepted, and only with the other party.
        boolean contactsOpen = Set.of("CONFIRMED", "DISPATCHED", "DELIVERED", "COMPLETED").contains(s);
        Farmer buyer = people.get(o.getBuyerId());
        Farmer seller = people.get(o.getSellerId());
        String buyerContact = contactsOpen && !isBuyer && buyer != null ? buyer.getContact() : null;
        String sellerContact = contactsOpen && !isSeller && seller != null ? seller.getContact() : null;
        // The one-time code is for the buyer's eyes only, and only while the delivery is under way.
        String code = isBuyer && (s.equals("CONFIRMED") || s.equals("DISPATCHED")) ? o.getDeliveryCode() : null;

        List<String> actions = new ArrayList<>();
        if (isSeller) {
            switch (s) {
                case "PENDING" -> { actions.add("confirm"); actions.add("decline"); }
                case "CONFIRMED" -> { actions.add("dispatch"); actions.add("cancel"); }
                case "DISPATCHED" -> actions.add("deliver");
                case "DELIVERED" -> { if (!o.isSellerConfirmedPaid()) actions.add("confirm-payment"); }
                default -> { }
            }
        } else if (isBuyer) {
            switch (s) {
                case "PENDING", "CONFIRMED" -> actions.add("cancel");
                case "DELIVERED" -> { if (!o.isBuyerConfirmedPaid()) actions.add("confirm-payment"); }
                default -> { }
            }
        }

        List<EventView> eventViews = es.stream().map(e -> new EventView(
                e.getActorId() == null ? "System" : people.containsKey(e.getActorId()) ? people.get(e.getActorId()).getName() : "Farmer",
                e.getFromStatus(), e.getToStatus(), e.getNote(), e.getCreatedAt())).toList();

        // Cost and profit belong to the seller (and admins); the buyer never sees them.
        BigDecimal cost = isBuyer ? null : o.getCostTotal();
        BigDecimal profit = cost == null ? null : o.getTotalAmount().subtract(cost);
        boolean canReview = isBuyer && "COMPLETED".equals(s) && !reviewed;

        return new OrderView(o.getId(), o.getListingId(), o.getProduct(), o.getUnit(), o.getPricePerUnit(), o.getQuantity(),
                o.getTotalAmount(), s, role,
                o.getBuyerId(), buyer != null ? buyer.getName() : "Buyer", buyerContact,
                o.getSellerId(), seller != null ? seller.getName() : "Farmer", sellerContact,
                o.getDeliveryNote(), code, o.isBuyerConfirmedPaid(), o.isSellerConfirmedPaid(), actions,
                o.getCreatedAt(), o.getUpdatedAt(), o.getDeliveredAt(), o.getCompletedAt(),
                imageId, cost, profit, reviewed, canReview, eventViews);
    }

    private static String noteOf(String base, String reason) {
        return reason == null || reason.isBlank() ? base : base + ": " + reason.trim();
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

    private static ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }
}
