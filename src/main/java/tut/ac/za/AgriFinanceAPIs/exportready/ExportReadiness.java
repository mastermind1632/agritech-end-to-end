package tut.ac.za.AgriFinanceAPIs.exportready;

import java.util.*;

/**
 * Scoring and stage rules for an export workflow. Pure Java.
 * A stage is complete when every applicable item in it is DONE (NOT_APPLICABLE items are ignored).
 * The workflow may only move to the next stage when the current one is complete.
 */
public final class ExportReadiness {

    public static final List<String> STAGES = List.of(
            "Eligibility", "Market access", "Registrations", "Production compliance",
            "Shipment preparation", "Inspection", "Customs clearance", "Shipped and closed");

    public static final Map<String, String> AUTHORITIES = new LinkedHashMap<>();
    static {
        AUTHORITIES.put("PLATFORM", "Platform");
        AUTHORITIES.put("PPECB", "PPECB");
        AUTHORITIES.put("DEPT_AGRICULTURE", "Department of Agriculture");
        AUTHORITIES.put("SARS_CUSTOMS", "SARS Customs");
        AUTHORITIES.put("DESTINATION", "Destination country");
        AUTHORITIES.put("PRIVATE_STANDARD", "Buyers and private standards");
        AUTHORITIES.put("FORWARDER", "Freight forwarder");
    }

    public record Item(int stage, String status) { }

    public record Score(int percent, int done, int applicable, Map<Integer, Integer> stagePercent, int firstIncompleteStage) { }

    private ExportReadiness() { }

    public static Score score(List<Item> items) {
        int done = 0, applicable = 0;
        Map<Integer, int[]> perStage = new TreeMap<>();
        for (int s = 1; s <= STAGES.size(); s++) perStage.put(s, new int[2]);
        for (Item i : items) {
            if ("NOT_APPLICABLE".equals(i.status())) continue;
            int[] c = perStage.computeIfAbsent(i.stage(), k -> new int[2]);
            c[1]++;
            applicable++;
            if ("DONE".equals(i.status())) { c[0]++; done++; }
        }
        Map<Integer, Integer> stagePercent = new TreeMap<>();
        int firstIncomplete = STAGES.size() + 1;
        for (Map.Entry<Integer, int[]> e : perStage.entrySet()) {
            int[] c = e.getValue();
            int pct = c[1] == 0 ? 100 : (int) Math.round(100.0 * c[0] / c[1]);
            stagePercent.put(e.getKey(), pct);
            if (pct < 100 && e.getKey() < firstIncomplete) firstIncomplete = e.getKey();
        }
        int percent = applicable == 0 ? 0 : (int) Math.round(100.0 * done / applicable);
        return new Score(percent, done, applicable, stagePercent, firstIncomplete);
    }

    public static boolean stageComplete(List<Item> items, int stage) {
        for (Item i : items) {
            if (i.stage() == stage && !"DONE".equals(i.status()) && !"NOT_APPLICABLE".equals(i.status())) return false;
        }
        return true;
    }

    public static boolean canAdvance(List<Item> items, int currentStage) {
        return currentStage < STAGES.size() && stageComplete(items, currentStage);
    }

    /** "Export-ready" badge: everything up to and including inspection is done. */
    public static boolean exportReady(List<Item> items) {
        for (int s = 1; s <= 6; s++) if (!stageComplete(items, s)) return false;
        return !items.isEmpty();
    }
}
