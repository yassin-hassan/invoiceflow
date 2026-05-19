package com.example.invoiceflow.admin.dto;

import com.example.invoiceflow.audit.AuditAction;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class AuditLogResponse {
    private UUID id;
    private LocalDateTime occurredAt;
    private UUID actorUserId;
    private String actorEmail;
    private AuditAction action;
    private String resourceType;
    private String resourceId;
    private String ipAddress;
    private String userAgent;
    private Map<String, Object> details;
}
