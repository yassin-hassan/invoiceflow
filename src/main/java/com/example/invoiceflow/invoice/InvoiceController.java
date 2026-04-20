package com.example.invoiceflow.invoice;

import com.example.invoiceflow.invoice.dto.CreateInvoiceRequest;
import com.example.invoiceflow.invoice.dto.InvoiceResponse;
import com.example.invoiceflow.invoice.dto.RecordPaymentRequest;
import com.example.invoiceflow.invoice.dto.UpdateInvoiceRequest;
import com.example.invoiceflow.invoice.dto.UpdateInvoiceStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceMapper invoiceMapper;

    @GetMapping("/api/invoices")
    public ResponseEntity<List<InvoiceResponse>> getInvoices(
            @AuthenticationPrincipal UserDetails principal) {
        List<InvoiceResponse> invoices = invoiceService.getInvoices(principal.getUsername())
                .stream()
                .map(invoiceMapper::toResponse)
                .toList();
        return ResponseEntity.ok(invoices);
    }

    @GetMapping("/api/invoices/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        Invoice invoice = invoiceService.getInvoice(principal.getUsername(), id);
        return ResponseEntity.ok(invoiceMapper.toResponse(invoice));
    }

    @PostMapping("/api/invoices")
    public ResponseEntity<InvoiceResponse> createInvoice(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateInvoiceRequest request) {
        Invoice created = invoiceService.createInvoice(principal.getUsername(), request);
        return ResponseEntity.status(201).body(invoiceMapper.toResponse(created));
    }

    @PostMapping("/api/quotes/{quoteId}/convert")
    public ResponseEntity<InvoiceResponse> convertQuote(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID quoteId) {
        Invoice invoice = invoiceService.convertFromQuote(principal.getUsername(), quoteId);
        return ResponseEntity.status(201).body(invoiceMapper.toResponse(invoice));
    }

    @PutMapping("/api/invoices/{id}")
    public ResponseEntity<InvoiceResponse> updateInvoice(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInvoiceRequest request) {
        Invoice updated = invoiceService.updateInvoice(principal.getUsername(), id, request);
        return ResponseEntity.ok(invoiceMapper.toResponse(updated));
    }

    @PatchMapping("/api/invoices/{id}/status")
    public ResponseEntity<InvoiceResponse> updateStatus(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInvoiceStatusRequest request) {
        Invoice updated = invoiceService.updateStatus(principal.getUsername(), id, request);
        return ResponseEntity.ok(invoiceMapper.toResponse(updated));
    }

    @DeleteMapping("/api/invoices/{id}")
    public ResponseEntity<Void> deleteInvoice(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        invoiceService.deleteInvoice(principal.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/invoices/{id}/payments")
    public ResponseEntity<InvoiceResponse> recordPayment(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody RecordPaymentRequest request) {
        Invoice updated = invoiceService.recordPayment(principal.getUsername(), id, request);
        return ResponseEntity.status(201).body(invoiceMapper.toResponse(updated));
    }
}
