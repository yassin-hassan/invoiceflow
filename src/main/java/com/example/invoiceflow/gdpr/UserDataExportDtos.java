package com.example.invoiceflow.gdpr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO shapes for the GDPR data export zip. One record per JSON file we emit.
 * Defined as nested records so the export structure is documented in one place.
 *
 * Field names are kept identical to the entity field names so users can correlate
 * with anything they may already see in the app's UI / API responses.
 */
final class UserDataExportDtos {

    private UserDataExportDtos() {}

    record AddressDto(String street, String postalCode, String city, String country) {}

    record ProfileDto(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String companyName,
            String phone,
            String vatNumber,
            String logoUrl,
            AddressDto billingAddress,
            String preferredLanguage,
            String role,
            boolean emailVerified,
            boolean twoFaEnabled,
            String twoFaPhone,
            LocalDateTime createdAt,
            LocalDateTime lastLoginAt
    ) {}

    record ClientDto(
            UUID id,
            String name,
            String email,
            String phone,
            String vatNumber,
            AddressDto billingAddress,
            String notes,
            boolean active,
            LocalDateTime createdAt
    ) {}

    record InvoiceLineDto(
            UUID id,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal vatRate,
            int sortOrder
    ) {}

    record PaymentDto(
            UUID id,
            BigDecimal amount,
            String method,
            LocalDate paidAt,
            String notes,
            LocalDateTime createdAt
    ) {}

    record InvoiceDto(
            UUID id,
            String number,
            String status,
            UUID clientId,
            String clientName,
            UUID quoteId,
            LocalDate issueDate,
            LocalDate dueDate,
            String paymentTerms,
            LocalDateTime sentAt,
            LocalDateTime createdAt,
            List<InvoiceLineDto> lines,
            List<PaymentDto> payments
    ) {}

    record QuoteLineDto(
            UUID id,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal vatRate,
            int sortOrder
    ) {}

    record QuoteDto(
            UUID id,
            String number,
            String status,
            UUID clientId,
            String clientName,
            LocalDate issueDate,
            LocalDate expiryDate,
            String notes,
            LocalDateTime createdAt,
            List<QuoteLineDto> lines
    ) {}

    record CreditNoteLineDto(
            UUID id,
            UUID invoiceLineId,
            BigDecimal quantity,
            int sortOrder
    ) {}

    record CreditNoteDto(
            UUID id,
            String number,
            String status,
            UUID originalInvoiceId,
            String originalInvoiceNumber,
            LocalDate issueDate,
            String reason,
            LocalDateTime issuedAt,
            LocalDateTime createdAt,
            List<CreditNoteLineDto> lines
    ) {}

    record ProductDto(
            UUID id,
            String name,
            String description,
            String reference,
            BigDecimal unitPrice,
            BigDecimal vatRate,
            String unit,
            boolean active,
            LocalDateTime createdAt
    ) {}

    record AuditLogDto(
            UUID id,
            LocalDateTime occurredAt,
            String action,
            String resourceType,
            String resourceId,
            String ipAddress,
            String userAgent,
            Map<String, Object> details
    ) {}
}
