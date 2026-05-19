package com.example.invoiceflow.admin;

import com.example.invoiceflow.admin.dto.AuditLogResponse;
import com.example.invoiceflow.audit.AuditAction;
import com.example.invoiceflow.audit.AuditLog;
import com.example.invoiceflow.audit.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private static final int CSV_ROW_CAP = 50_000;

    private final AuditLogRepository auditLogRepository;

    public Page<AuditLogResponse> search(AuditLogFilters filters, Pageable pageable) {
        Pageable sortedByOccurred = withDescOccurred(pageable);
        return auditLogRepository.findAll(toSpec(filters), sortedByOccurred).map(this::toResponse);
    }

    public List<AuditLogResponse> exportCsv(AuditLogFilters filters) {
        Pageable cap = Pageable.ofSize(CSV_ROW_CAP).withPage(0);
        return auditLogRepository.findAll(toSpec(filters), withDescOccurred(cap))
                .map(this::toResponse).getContent();
    }

    private Pageable withDescOccurred(Pageable pageable) {
        Sort sort = Sort.by(Sort.Order.desc("occurredAt"));
        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private Specification<AuditLog> toSpec(AuditLogFilters filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filters.actor() != null && !filters.actor().isBlank()) {
                String pattern = "%" + filters.actor().toLowerCase().trim() + "%";
                predicates.add(cb.like(cb.lower(root.get("actorEmail")), pattern));
            }
            if (filters.action() != null) {
                predicates.add(cb.equal(root.get("action"), filters.action()));
            }
            if (filters.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), filters.from()));
            }
            if (filters.to() != null) {
                predicates.add(cb.lessThan(root.get("occurredAt"), filters.to()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private AuditLogResponse toResponse(AuditLog log) {
        AuditLogResponse r = new AuditLogResponse();
        r.setId(log.getId());
        r.setOccurredAt(log.getOccurredAt());
        r.setActorUserId(log.getActorUserId());
        r.setActorEmail(log.getActorEmail());
        r.setAction(log.getAction());
        r.setResourceType(log.getResourceType());
        r.setResourceId(log.getResourceId());
        r.setIpAddress(log.getIpAddress());
        r.setUserAgent(log.getUserAgent());
        r.setDetails(log.getDetails());
        return r;
    }

    public record AuditLogFilters(String actor, AuditAction action, LocalDateTime from, LocalDateTime to) {}
}
