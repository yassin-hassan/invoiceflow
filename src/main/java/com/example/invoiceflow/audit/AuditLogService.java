package com.example.invoiceflow.audit;

import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final RequestContext requestContext;

    public void record(AuditAction action, String resourceType, String resourceId, Map<String, Object> details) {
        AuditLog entry = baseEntry(action, resourceType, resourceId, details);
        resolveActor(entry);
        auditLogRepository.save(entry);
    }

    public void recordAnonymous(AuditAction action, String resourceType, String resourceId, Map<String, Object> details) {
        AuditLog entry = baseEntry(action, resourceType, resourceId, details);
        auditLogRepository.save(entry);
    }

    /**
     * Record an entry whose actor is known by email rather than via the SecurityContext —
     * used when the principal hasn't been put into the security context yet (e.g. LOGIN_SUCCESS
     * fires before the JWT is issued).
     */
    public void recordForEmail(AuditAction action, String email, String resourceType, String resourceId, Map<String, Object> details) {
        AuditLog entry = baseEntry(action, resourceType, resourceId, details);
        applyExplicitActor(entry, email);
        auditLogRepository.save(entry);
    }

    /**
     * Record an entry that must persist independently of any outer transaction —
     * used for events whose meaning is "this attempt happened" even when the surrounding
     * operation rolls back (e.g. LOGIN_FAILED).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependentForEmail(AuditAction action, String email, String resourceType, String resourceId, Map<String, Object> details) {
        AuditLog entry = baseEntry(action, resourceType, resourceId, details);
        applyExplicitActor(entry, email);
        auditLogRepository.save(entry);
    }

    private AuditLog baseEntry(AuditAction action, String resourceType, String resourceId, Map<String, Object> details) {
        AuditLog entry = new AuditLog();
        entry.setOccurredAt(LocalDateTime.now());
        entry.setAction(action);
        entry.setResourceType(resourceType);
        entry.setResourceId(resourceId);
        entry.setIpAddress(requestContext.ipAddress());
        entry.setUserAgent(requestContext.userAgent());
        entry.setDetails(details);
        return entry;
    }

    private void resolveActor(AuditLog entry) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return;
        Object principal = auth.getPrincipal();
        if (!(principal instanceof UserDetails ud)) return;
        applyExplicitActor(entry, ud.getUsername());
    }

    private void applyExplicitActor(AuditLog entry, String email) {
        if (email == null || email.isBlank()) return;
        String normalised = email.toLowerCase().trim();
        entry.setActorEmail(normalised);
        UUID id = userRepository.findByEmail(normalised).map(User::getId).orElse(null);
        entry.setActorUserId(id);
    }
}
