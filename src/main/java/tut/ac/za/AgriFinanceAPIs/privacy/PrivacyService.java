package tut.ac.za.AgriFinanceAPIs.privacy;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tut.ac.za.AgriFinanceAPIs.common.AuditService;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PrivacyService {

    /** Bump when the wording of the terms or privacy notice changes; users are then asked again. */
    public static final String VERSION = "2026-10";

    public static final Map<String, String> PURPOSES = new LinkedHashMap<>();
    static {
        PURPOSES.put("TERMS", "Terms of use");
        PURPOSES.put("PRIVACY", "Privacy notice (POPIA)");
        PURPOSES.put("STATEMENT_PROCESSING", "Read my uploaded bank statements to categorise and analyse my spending");
        PURPOSES.put("ANALYTICS_SHARING", "Use my data in aggregated, anonymised form to improve market insights");
        PURPOSES.put("MARKETING", "Send me news about new features and offers");
    }

    public record ConsentView(String purpose, String label, boolean granted, String version, LocalDateTime at) { }

    private final ConsentRepository consents;
    private final DataRequestRepository requests;
    private final AuditService audit;

    public PrivacyService(ConsentRepository consents, DataRequestRepository requests, AuditService audit) {
        this.consents = consents;
        this.requests = requests;
        this.audit = audit;
    }

    @Transactional
    public ConsentRecord record(String farmerId, String purpose, boolean granted) {
        String p = purpose == null ? "" : purpose.trim().toUpperCase();
        if (!PURPOSES.containsKey(p)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown consent purpose");
        ConsentRecord r = new ConsentRecord();
        r.setFarmerId(farmerId);
        r.setPurpose(p);
        r.setGranted(granted);
        r.setVersion(VERSION);
        r.setIp(AuditService.clientIp());
        ConsentRecord saved = consents.save(r);
        audit.record(farmerId, granted ? "CONSENT_GRANTED" : "CONSENT_WITHDRAWN", "CONSENT", p, null);
        return saved;
    }

    public boolean hasConsent(String farmerId, String purpose) {
        return consents.findFirstByFarmerIdAndPurposeOrderByCreatedAtDescIdDesc(farmerId, purpose)
                .map(ConsentRecord::isGranted).orElse(false);
    }

    public List<ConsentView> current(String farmerId) {
        List<ConsentView> out = new ArrayList<>();
        for (Map.Entry<String, String> e : PURPOSES.entrySet()) {
            Optional<ConsentRecord> r = consents.findFirstByFarmerIdAndPurposeOrderByCreatedAtDescIdDesc(farmerId, e.getKey());
            out.add(new ConsentView(e.getKey(), e.getValue(), r.map(ConsentRecord::isGranted).orElse(false),
                    r.map(ConsentRecord::getVersion).orElse(null), r.map(ConsentRecord::getCreatedAt).orElse(null)));
        }
        return out;
    }

    @Transactional
    public DataRequest requestDeletion(String farmerId, String reason) {
        if (requests.existsByFarmerIdAndStatus(farmerId, "OPEN")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a deletion request waiting");
        }
        DataRequest r = new DataRequest();
        r.setFarmerId(farmerId);
        r.setRequestType("DELETE_ACCOUNT");
        r.setStatus("OPEN");
        r.setReason(reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason);
        DataRequest saved = requests.save(r);
        audit.record(farmerId, "DELETION_REQUESTED", "DATA_REQUEST", String.valueOf(saved.getId()), null);
        return saved;
    }
}
