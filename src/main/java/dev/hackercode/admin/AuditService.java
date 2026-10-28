package dev.hackercode.admin;

import dev.hackercode.common.Requests;
import dev.hackercode.user.HcPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records security-relevant and administrative events. Callers pass short descriptive details only —
 * never passwords, tokens, secrets or source code.
 */
@Service
public class AuditService {
    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    /** Record an event attributed to the currently authenticated user (if any). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String targetType, Object targetId, String detail) {
        Long actorId = null;
        String actorEmail = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HcPrincipal p) {
            actorId = p.id();
            actorEmail = p.email();
        }
        recordAs(actorId, actorEmail, action, targetType, targetId, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAs(Long actorId, String actorEmail, String action, String targetType, Object targetId, String detail) {
        String ip = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            ip = Requests.ipHint(request);
        }
        String safeDetail = detail != null && detail.length() > 2000 ? detail.substring(0, 2000) : detail;
        repository.save(new AuditEvent(actorId, actorEmail, action, targetType,
                targetId == null ? null : String.valueOf(targetId), safeDetail, ip));
    }
}
