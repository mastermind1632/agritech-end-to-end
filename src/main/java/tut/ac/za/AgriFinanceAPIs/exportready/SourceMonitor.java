package tut.ac.za.AgriFinanceAPIs.exportready;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tut.ac.za.AgriFinanceAPIs.common.Hashing;
import tut.ac.za.AgriFinanceAPIs.notification.NotificationHub;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Watches the official source page of every requirement (like changedetection.io). When a page changes, a change
 * proposal is created for a compliance editor to review. Nothing is published to farmers automatically.
 * Off by default (APP_EXPORT_MONITOR_ENABLED=true to switch on); the server needs outbound internet access.
 */
@Component
public class SourceMonitor {

    private static final Logger log = LoggerFactory.getLogger(SourceMonitor.class);

    private final ExportRequirementRepository requirements;
    private final ExportRequirementChangeRepository changes;
    private final NotificationHub notifications;
    private final RestClient http;
    private final boolean enabled;

    public SourceMonitor(ExportRequirementRepository requirements, ExportRequirementChangeRepository changes,
                         NotificationHub notifications, RestClient.Builder builder,
                         @Value("${app.export.monitor.enabled:false}") boolean enabled) {
        this.requirements = requirements;
        this.changes = changes;
        this.notifications = notifications;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = builder.clone().requestFactory(factory).defaultHeader("User-Agent", "AgriTech-compliance-monitor/1.0").build();
        this.enabled = enabled;
    }

    @Scheduled(cron = "${app.export.monitor.cron:0 15 2 * * *}")
    public void scheduled() {
        if (enabled) check(false);
    }

    /** Returns the number of new change proposals. Each distinct URL is fetched once per run. */
    public int check(boolean manual) {
        List<ExportRequirement> list = requirements.findByActiveTrueAndSourceUrlIsNotNull();
        Map<String, String> pageHashes = new HashMap<>();
        int detected = 0;
        for (ExportRequirement r : list) {
            String url = r.getSourceUrl();
            String hash = pageHashes.computeIfAbsent(url, this::fetchHash);
            if (hash == null) continue;
            String previous = r.getSourceHash();
            r.setSourceHash(hash);
            r.setSourceCheckedAt(LocalDateTime.now());
            requirements.save(r);
            if (previous != null && !previous.equals(hash) && !changes.existsByRequirementIdAndStatus(r.getId(), "PENDING")) {
                ExportRequirementChange c = new ExportRequirementChange();
                c.setRequirementId(r.getId());
                c.setSource("MONITOR");
                c.setSummary("The official source page changed: " + url + ". Check whether this requirement is still correct.");
                c.setStatus("PENDING");
                changes.save(c);
                detected++;
            }
        }
        if (detected > 0) {
            notifications.notifyAdmins("EXPORT", detected + " export requirement source(s) changed",
                    "Review them in Admin > Content review before farmers are told.", "admin");
        }
        log.info("Export source monitor ({}): {} requirements, {} changes", manual ? "manual" : "scheduled", list.size(), detected);
        return detected;
    }

    private String fetchHash(String url) {
        try {
            String body = http.get().uri(url).retrieve().body(String.class);
            if (body == null) return null;
            String text = body.replaceAll("(?is)<script.*?</script>", " ").replaceAll("(?is)<style.*?</style>", " ")
                    .replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            return Hashing.sha256(text);
        } catch (RuntimeException e) {
            log.debug("Could not fetch {}: {}", url, e.getMessage());
            return null;
        }
    }
}
