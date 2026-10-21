package tut.ac.za.AgriFinanceAPIs.marketplace;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;
import tut.ac.za.AgriFinanceAPIs.farmer.Farmer;
import tut.ac.za.AgriFinanceAPIs.farmer.FarmerRepository;
import tut.ac.za.AgriFinanceAPIs.marketplace.DisputeDtos.*;
import tut.ac.za.AgriFinanceAPIs.notification.NotificationHub;
import tut.ac.za.AgriFinanceAPIs.vault.VaultController;
import tut.ac.za.AgriFinanceAPIs.vault.VaultDocumentRepository;
import tut.ac.za.AgriFinanceAPIs.vault.VaultService;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Disputes: each has a reason, both sides can upload evidence, the other side has a response deadline,
 * and an administrator resolves it. Every step is written to the order's event trail and the audit log.
 */
@Service
public class DisputeService {

    public static final Map<String, String> REASONS = new LinkedHashMap<>();
    private static final Set<String> BUYER_REASONS = Set.of("QUALITY", "SHORT_QUANTITY", "NOT_DELIVERED", "DAMAGED", "OTHER");
    private static final Set<String> SELLER_REASONS = Set.of("NO_SHOW", "NON_PAYMENT", "OTHER");
    public static final Set<String> RESOLUTIONS = Set.of("BUYER_FAVOUR", "SELLER_FAVOUR", "SPLIT");
    static final Set<String> OPEN = Set.of("OPEN", "RESPONDED");
    static final int DISPUTE_WINDOW_DAYS = 14;
    static final int RESPONSE_HOURS = 72;

    static {
        REASONS.put("QUALITY", "Quality not as described");
        REASONS.put("SHORT_QUANTITY", "Short quantity");
        REASONS.put("NOT_DELIVERED", "Not delivered");
        REASONS.put("DAMAGED", "Damaged in transit");
        REASONS.put("NO_SHOW", "Buyer did not show up");
        REASONS.put("NON_PAYMENT", "Buyer did not pay");
        REASONS.put("OTHER", "Other");
    }

    private final MarketDisputeRepository disputes;
    private final MarketOrderRepository orders;
    private final MarketOrderEventRepository events;
    private final ListingRepository listings;
    private final FarmerRepository farmers;
    private final NotificationHub notifications;
    private final AuditService audit;
    private final VaultService vault;
    private final VaultDocumentRepository documents;

    public DisputeService(MarketDisputeRepository disputes, MarketOrderRepository orders, MarketOrderEventRepository events,
                          ListingRepository listings, FarmerRepository farmers, NotificationHub notifications,
                          AuditService audit, VaultService vault, VaultDocumentRepository documents) {
        this.disputes = disputes;
        this.orders = orders;
        this.events = events;
        this.listings = listings;
        this.farmers = farmers;
        this.notifications = notifications;
        this.audit = audit;
        this.vault = vault;
        this.documents = documents;
    }

    /** True when the viewer may raise a dispute on this order right now (used for the order's action buttons). */
    public static boolean canRaise(MarketOrder o, String viewer, boolean hasOpenDispute) {
        if (hasOpenDispute) return false;
        boolean buyer = viewer.equals(o.getBuyerId());
        boolean seller = viewer.equals(o.getSellerId());
        if (!buyer && !seller) return false;
        LocalDateTime now = LocalDateTime.now();
        return switch (o.getStatus()) {
            case "DISPATCHED" -> buyer || seller;
            case "DELIVERED" -> true;
            case "COMPLETED" -> o.getCompletedAt() != null && o.getCompletedAt().plusDays(DISPUTE_WINDOW_DAYS).isAfter(now);
            default -> false;
        };
    }

    @Transactional
    public DisputeView raise(Long orderId, String actor, RaiseDispute req) {
        MarketOrder o = orders.findForUpdate(orderId).orElseThrow(() -> notFound("Order not found"));
        if (!actor.equals(o.getBuyerId()) && !actor.equals(o.getSellerId())) throw notFound("Order not found");
        boolean buyer = actor.equals(o.getBuyerId());
        String reason = req == null || req.reason() == null ? "" : req.reason().trim().toUpperCase();
        if (!(buyer ? BUYER_REASONS : SELLER_REASONS).contains(reason)) {
            throw bad("Choose a reason: " + String.join(", ", (buyer ? BUYER_REASONS : SELLER_REASONS)).toLowerCase().replace('_', ' '));
        }
        String description = req.description() == null ? "" : req.description().trim();
        if (description.length() < 10) throw bad("Please describe what happened (at least 10 characters)");
        if (description.length() > 1000) throw bad("Description is too long (max 1000 characters)");
        boolean open = disputes.findByOrderIdInOrderByCreatedAtDesc(List.of(orderId)).stream().anyMatch(MarketDispute::isOpen);
        if (!canRaise(o, actor, open)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, open ? "This order already has an open dispute"
                    : "A dispute can be raised while an order is on its way, once delivered, or within "
                    + DISPUTE_WINDOW_DAYS + " days of completion");
        }

        MarketDispute d = new MarketDispute();
        d.setOrderId(orderId);
        d.setRaisedBy(actor);
        d.setAgainstId(buyer ? o.getSellerId() : o.getBuyerId());
        d.setReason(reason);
        d.setDescription(description);
        d.setStatus("OPEN");
        d.setPreviousStatus(o.getStatus());
        d.setRespondBy(LocalDateTime.now().plusHours(RESPONSE_HOURS));
        MarketDispute saved = disputes.save(d);

        logEvent(o, actor, o.getStatus(), "DISPUTED", "Dispute raised: " + REASONS.get(reason));
        o.setStatus("DISPUTED");
        orders.save(o);

        notifications.notify(d.getAgainstId(), "DISPUTE", "A dispute was raised on order #" + orderId,
                REASONS.get(reason) + ". Please respond within " + RESPONSE_HOURS + " hours with your side and any evidence.", "disputes");
        notifications.notifyAdmins("DISPUTE", "New dispute on order #" + orderId, REASONS.get(reason) + ": " + description, "admin");
        audit.record(actor, "DISPUTE_RAISED", "DISPUTE", String.valueOf(saved.getId()), "order " + orderId + " " + reason);
        return view(saved, actor, false);
    }

    @Transactional
    public DisputeView respond(Long id, String actor, Respond req) {
        MarketDispute d = lock(id);
        if (!actor.equals(d.getAgainstId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the other party can respond");
        if (!"OPEN".equals(d.getStatus())) throw conflict("This dispute has already been answered or closed");
        String text = req == null || req.response() == null ? "" : req.response().trim();
        if (text.length() < 5 || text.length() > 1000) throw bad("Write a response of 5 to 1000 characters");
        d.setResponse(text);
        d.setRespondedAt(LocalDateTime.now());
        d.setStatus("RESPONDED");
        disputes.save(d);
        notifications.notify(d.getRaisedBy(), "DISPUTE", "Response to your dispute on order #" + d.getOrderId(), text, "disputes");
        notifications.notifyAdmins("DISPUTE", "Dispute #" + id + " is ready for a decision", "Both sides have now given their account.", "admin");
        audit.record(actor, "DISPUTE_RESPONDED", "DISPUTE", String.valueOf(id), null);
        return view(d, actor, false);
    }

    @Transactional
    public DisputeView addEvidence(Long id, String actor, String fileName, byte[] bytes, String note) {
        MarketDispute d = disputes.findById(id).orElseThrow(() -> notFound("Dispute not found"));
        if (!actor.equals(d.getRaisedBy()) && !actor.equals(d.getAgainstId())) throw notFound("Dispute not found");
        if (!d.isOpen()) throw conflict("Evidence can only be added while the dispute is open");
        if (documents.linked("DISPUTE", String.valueOf(id)).size() >= 10) throw bad("A dispute can have at most 10 evidence files");
        vault.store(actor, note == null || note.isBlank() ? "Evidence for dispute #" + id : note, "EVIDENCE", fileName, bytes,
                null, "DISPUTE", String.valueOf(id));
        return view(d, actor, false);
    }

    @Transactional
    public DisputeView withdraw(Long id, String actor) {
        MarketDispute d = lock(id);
        if (!actor.equals(d.getRaisedBy())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the person who raised it can withdraw it");
        if (!d.isOpen()) throw conflict("This dispute is already closed");
        d.setStatus("WITHDRAWN");
        d.setResolvedAt(LocalDateTime.now());
        disputes.save(d);
        restoreOrder(d, d.getPreviousStatus(), actor, "Dispute withdrawn", false);
        notifications.notify(d.getAgainstId(), "DISPUTE", "Dispute on order #" + d.getOrderId() + " was withdrawn", null, "disputes");
        audit.record(actor, "DISPUTE_WITHDRAWN", "DISPUTE", String.valueOf(id), null);
        return view(d, actor, false);
    }

    /**
     * Admin decision. BUYER_FAVOUR cancels an unpaid order (and returns stock if it never arrived);
     * SELLER_FAVOUR and SPLIT put the order back where it was so the normal flow can continue.
     */
    @Transactional
    public DisputeView resolve(Long id, String admin, Resolve req) {
        MarketDispute d = lock(id);
        if (!d.isOpen()) throw conflict("This dispute is already closed");
        String resolution = req == null || req.resolution() == null ? "" : req.resolution().trim().toUpperCase();
        if (!RESOLUTIONS.contains(resolution)) throw bad("Resolution must be BUYER_FAVOUR, SELLER_FAVOUR or SPLIT");
        String note = req.note() == null ? "" : req.note().trim();
        if (note.length() < 5) throw bad("Explain the decision to both parties (at least 5 characters)");
        d.setResolution(resolution);
        d.setResolutionNote(note.length() > 1000 ? note.substring(0, 1000) : note);
        d.setResolvedBy(admin);
        d.setResolvedAt(LocalDateTime.now());
        d.setStatus("RESOLVED");
        disputes.save(d);

        MarketOrder o = orders.findById(d.getOrderId()).orElseThrow();
        String target = d.getPreviousStatus();
        boolean releaseStock = false;
        if ("BUYER_FAVOUR".equals(resolution) && !"COMPLETED".equals(d.getPreviousStatus())) {
            target = "CANCELLED";
            releaseStock = "NOT_DELIVERED".equals(d.getReason());
        }
        restoreOrder(d, target, admin, "Dispute resolved (" + resolution.replace('_', ' ').toLowerCase() + "): " + note, releaseStock);

        String title = "Decision on the dispute for order #" + d.getOrderId();
        String body = switch (resolution) {
            case "BUYER_FAVOUR" -> "Decided in the buyer's favour. " + note;
            case "SELLER_FAVOUR" -> "Decided in the farmer's favour. " + note;
            default -> "Settled between both parties. " + note;
        };
        notifications.notify(o.getBuyerId(), "DISPUTE", title, body, "disputes");
        notifications.notify(o.getSellerId(), "DISPUTE", title, body, "disputes");
        audit.record(admin, "DISPUTE_RESOLVED", "DISPUTE", String.valueOf(id), resolution + " -> order " + target);
        return view(d, admin, true);
    }

    private void restoreOrder(MarketDispute d, String target, String actor, String note, boolean releaseStock) {
        orders.findForUpdate(d.getOrderId()).ifPresent(o -> {
            if (!"DISPUTED".equals(o.getStatus())) return;
            if (releaseStock && o.isStockReserved()) {
                listings.findForUpdate(o.getListingId()).ifPresent(l -> {
                    l.setQuantityAvailable(l.getQuantityAvailable().add(o.getQuantity()));
                    if ("SOLD_OUT".equals(l.getStatus()) && l.getQuantityAvailable().signum() > 0) l.setStatus("ACTIVE");
                    listings.save(l);
                });
                o.setStockReserved(false);
            }
            logEvent(o, actor, "DISPUTED", target, note);
            o.setStatus(target);
            orders.save(o);
        });
    }

    /** Disputes still unanswered after the deadline are escalated to administrators once a day. */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void escalateOverdue() {
        LocalDateTime now = LocalDateTime.now();
        for (MarketDispute d : disputes.findByStatusInOrderByCreatedAtAsc(List.of("OPEN"))) {
            if (d.getRespondBy().isBefore(now)) {
                notifications.notifyAdmins("DISPUTE", "Dispute #" + d.getId() + " has passed its response deadline",
                        "The other party has not responded. It can now be decided on the evidence available.", "admin");
            }
        }
    }

    // ---------------------------------------------------------------- reading

    @Transactional(readOnly = true)
    public List<DisputeView> mine(String user) {
        return views(disputes.forUser(user), user, false);
    }

    @Transactional(readOnly = true)
    public List<DisputeView> all(boolean openOnly, String admin) {
        List<MarketDispute> list = openOnly ? disputes.findByStatusInOrderByCreatedAtAsc(OPEN) : disputes.findAllByOrderByCreatedAtDesc();
        return views(list, admin, true);
    }

    @Transactional(readOnly = true)
    public DisputeView get(Long id, String viewer, boolean admin) {
        MarketDispute d = disputes.findById(id).orElseThrow(() -> notFound("Dispute not found"));
        if (!admin && !viewer.equals(d.getRaisedBy()) && !viewer.equals(d.getAgainstId())) throw notFound("Dispute not found");
        return view(d, viewer, admin);
    }

    /** Buyer reliability for sellers: completed orders against cancellations and disputes lost for no-show or non-payment. */
    @Transactional(readOnly = true)
    public Integer buyerReliability(String buyerId) {
        long completed = orders.countByBuyerIdAndStatus(buyerId, "COMPLETED");
        long cancelled = orders.countByBuyerIdAndStatus(buyerId, "CANCELLED");
        long lost = disputes.lostAsBuyer(buyerId);
        long total = completed + cancelled + lost;
        if (total == 0) return null;
        return (int) Math.round(100.0 * completed / total);
    }

    private DisputeView view(MarketDispute d, String viewer, boolean admin) {
        return views(List.of(d), viewer, admin).get(0);
    }

    private List<DisputeView> views(List<MarketDispute> list, String viewer, boolean admin) {
        if (list.isEmpty()) return List.of();
        Set<String> people = new HashSet<>();
        list.forEach(d -> { people.add(d.getRaisedBy()); people.add(d.getAgainstId()); });
        Map<String, String> names = farmers.findAllById(people).stream().collect(Collectors.toMap(Farmer::getId, Farmer::getName));
        Map<Long, MarketOrder> orderMap = orders.findAllById(list.stream().map(MarketDispute::getOrderId).toList()).stream()
                .collect(Collectors.toMap(MarketOrder::getId, o -> o));
        List<DisputeView> out = new ArrayList<>();
        for (MarketDispute d : list) {
            MarketOrder o = orderMap.get(d.getOrderId());
            String role = viewer.equals(d.getRaisedBy()) ? "RAISED_BY_ME" : viewer.equals(d.getAgainstId()) ? "AGAINST_ME" : "ADMIN";
            List<String> actions = new ArrayList<>();
            if (d.isOpen()) {
                if ("AGAINST_ME".equals(role) && "OPEN".equals(d.getStatus())) actions.add("respond");
                if (!"ADMIN".equals(role)) actions.add("evidence");
                if ("RAISED_BY_ME".equals(role)) actions.add("withdraw");
                if (admin) actions.add("resolve");
            }
            List<Map<String, Object>> evidence = documents.linked("DISPUTE", String.valueOf(d.getId())).stream()
                    .map(i -> {
                        Map<String, Object> m = VaultController.view(i);
                        m.put("uploadedBy", names.getOrDefault(i.ownerId(), "Participant"));
                        return m;
                    }).toList();
            out.add(new DisputeView(d.getId(), d.getOrderId(), o == null ? null : o.getProduct(), o == null ? null : o.getTotalAmount(),
                    d.getReason(), REASONS.getOrDefault(d.getReason(), d.getReason()), d.getDescription(), d.getStatus(),
                    d.getPreviousStatus(), d.getRaisedBy(), names.getOrDefault(d.getRaisedBy(), "User"), d.getAgainstId(),
                    names.getOrDefault(d.getAgainstId(), "User"), role, d.getRespondBy(), d.getResponse(), d.getRespondedAt(),
                    d.getResolution(), d.getResolutionNote(), d.getResolvedAt(), d.getCreatedAt(), evidence, actions));
        }
        return out;
    }

    private void logEvent(MarketOrder o, String actor, String from, String to, String note) {
        MarketOrderEvent e = new MarketOrderEvent();
        e.setOrderId(o.getId());
        e.setActorId(actor);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setNote(note != null && note.length() > 300 ? note.substring(0, 300) : note);
        events.save(e);
    }

    private MarketDispute lock(Long id) {
        return disputes.findForUpdate(id).orElseThrow(() -> notFound("Dispute not found"));
    }

    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, m); }

    private static ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT, m); }

    private static ResponseStatusException notFound(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND, m); }
}
