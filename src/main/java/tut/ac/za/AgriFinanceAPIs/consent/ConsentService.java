package tut.ac.za.AgriFinanceAPIs.consent;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConsentService {

    public static final String DATA_PROCESSING = "DATA_PROCESSING";
    public static final String ANALYTICS = "ANALYTICS";
    public static final String MARKETING = "MARKETING";
    public static final String SHARING_EXPORT = "SHARING_EXPORT";
    public static final String CURRENT_VERSION = "v1.0";

    private final ConsentRecordRepository records;

    public ConsentService(ConsentRecordRepository records) {
        this.records = records;
    }

    @Transactional
    public ConsentRecord record(String farmerId, String type, boolean granted, String version, String ip) {
        ConsentRecord r = new ConsentRecord();
        r.setFarmerId(farmerId);
        r.setConsentType(type);
        r.setGranted(granted);
        r.setVersion(version == null || version.isBlank() ? CURRENT_VERSION : version);
        r.setIpAddress(ip);
        if (!granted) {
            r.setRevokedAt(LocalDateTime.now());
        }
        return records.save(r);
    }

    public boolean hasConsent(String farmerId, String type) {
        return records.findFirstByFarmerIdAndConsentTypeOrderByGrantedAtDesc(farmerId, type)
                .map(ConsentRecord::isGranted)
                .orElse(false);
    }

    public List<ConsentRecord> history(String farmerId) {
        return records.findByFarmerIdOrderByGrantedAtDesc(farmerId);
    }

    public Map<String, Boolean> currentState(String farmerId) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (String type : List.of(DATA_PROCESSING, ANALYTICS, MARKETING, SHARING_EXPORT)) {
            out.put(type, hasConsent(farmerId, type));
        }
        return out;
    }
}
