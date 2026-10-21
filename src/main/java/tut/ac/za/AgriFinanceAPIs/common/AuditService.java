package tut.ac.za.AgriFinanceAPIs.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Writes an audit entry in its own transaction, so failed operations (e.g. a rejected login) are still logged
     * and a problem with the audit table never breaks the user's action.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actorId, String action, String entityType, String entityId, String detail) {
        try {
            AuditLog entry = new AuditLog();
            entry.setActorId(actorId);
            entry.setAction(truncate(action, 60));
            entry.setEntityType(truncate(entityType, 40));
            entry.setEntityId(truncate(entityId, 64));
            entry.setDetail(truncate(detail, 500));
            entry.setIp(truncate(clientIp(), 64));
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Could not write audit entry {} {}: {}", action, entityId, e.getMessage());
        }
    }

    public static String clientIp() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) return null;
        HttpServletRequest req = sra.getRequest();
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        String real = req.getHeader("X-Real-IP");
        return real != null && !real.isBlank() ? real : req.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
