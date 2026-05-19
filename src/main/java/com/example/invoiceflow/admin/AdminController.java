package com.example.invoiceflow.admin;

import com.example.invoiceflow.admin.AuditLogQueryService.AuditLogFilters;
import com.example.invoiceflow.admin.dto.AdminUserDetail;
import com.example.invoiceflow.admin.dto.AdminUserListItem;
import com.example.invoiceflow.admin.dto.AuditLogResponse;
import com.example.invoiceflow.admin.dto.UpdateUserRoleRequest;
import com.example.invoiceflow.admin.dto.UpdateUserStatusRequest;
import com.example.invoiceflow.audit.AuditAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private static final int MAX_PAGE_SIZE = 200;
    private static final DateTimeFormatter CSV_TS = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter FILENAME_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AdminService adminService;
    private final AuditLogQueryService auditLogQueryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserListItem>> listUsers() {
        return ResponseEntity.ok(adminService.listUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserDetail> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getUser(id));
    }

    @PatchMapping("/users/{id}/status")
    public ResponseEntity<AdminUserDetail> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(adminService.updateStatus(id, request.getActive()));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<AdminUserDetail> updateRole(
            @PathVariable UUID id, @Valid @RequestBody UpdateUserRoleRequest request) {
        return ResponseEntity.ok(adminService.updateRole(id, request.getRole()));
    }

    @PostMapping("/users/{id}/password-reset")
    public ResponseEntity<Void> triggerPasswordReset(@PathVariable UUID id) {
        adminService.triggerPasswordReset(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogResponse>> auditLogs(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        AuditLogFilters filters = new AuditLogFilters(actor, action, from, to);
        return ResponseEntity.ok(auditLogQueryService.search(filters, PageRequest.of(Math.max(page, 0), safeSize)));
    }

    @GetMapping(value = "/audit-logs.csv", produces = "text/csv")
    public ResponseEntity<String> auditLogsCsv(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        AuditLogFilters filters = new AuditLogFilters(actor, action, from, to);
        List<AuditLogResponse> rows = auditLogQueryService.exportCsv(filters);
        String body = toCsv(rows);
        String filename = "audit-logs-" + LocalDateTime.now().format(FILENAME_TS) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    private String toCsv(List<AuditLogResponse> rows) {
        StringWriter sw = new StringWriter();
        sw.append("occurred_at,actor_email,action,resource_type,resource_id,ip_address,user_agent,details\n");
        for (AuditLogResponse r : rows) {
            sw.append(csv(r.getOccurredAt() == null ? "" : r.getOccurredAt().format(CSV_TS))).append(',');
            sw.append(csv(r.getActorEmail())).append(',');
            sw.append(csv(r.getAction() == null ? "" : r.getAction().name())).append(',');
            sw.append(csv(r.getResourceType())).append(',');
            sw.append(csv(r.getResourceId())).append(',');
            sw.append(csv(r.getIpAddress())).append(',');
            sw.append(csv(r.getUserAgent())).append(',');
            sw.append(csv(serialiseDetails(r.getDetails()))).append('\n');
        }
        return sw.toString();
    }

    private String serialiseDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) return "";
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private String csv(String value) {
        if (value == null) return "";
        boolean needsQuote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return needsQuote ? "\"" + escaped + "\"" : escaped;
    }
}
