package com.example.invoiceflow.creditnote;

import com.example.invoiceflow.creditnote.dto.CreateCreditNoteRequest;
import com.example.invoiceflow.creditnote.dto.CreditNoteResponse;
import com.example.invoiceflow.creditnote.dto.UpdateCreditNoteRequest;
import com.example.invoiceflow.pdf.CreditNotePdfService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CreditNoteController {

    private final CreditNoteService creditNoteService;
    private final CreditNoteMapper creditNoteMapper;
    private final CreditNotePdfService creditNotePdfService;

    @GetMapping("/api/credit-notes")
    public ResponseEntity<List<CreditNoteResponse>> list(
            @AuthenticationPrincipal UserDetails principal) {
        List<CreditNoteResponse> list = creditNoteService.list(principal.getUsername())
                .stream()
                .map(creditNoteMapper::toResponse)
                .toList();
        return ResponseEntity.ok(list);
    }

    @GetMapping("/api/credit-notes/{id}")
    public ResponseEntity<CreditNoteResponse> get(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        CreditNote creditNote = creditNoteService.get(principal.getUsername(), id);
        return ResponseEntity.ok(creditNoteMapper.toResponse(creditNote));
    }

    @PostMapping("/api/invoices/{invoiceId}/credit-notes")
    public ResponseEntity<CreditNoteResponse> create(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID invoiceId,
            @Valid @RequestBody CreateCreditNoteRequest request) {
        CreditNote created = creditNoteService.create(principal.getUsername(), invoiceId, request);
        return ResponseEntity.status(201).body(creditNoteMapper.toResponse(created));
    }

    @PutMapping("/api/credit-notes/{id}")
    public ResponseEntity<CreditNoteResponse> update(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCreditNoteRequest request) {
        CreditNote updated = creditNoteService.update(principal.getUsername(), id, request);
        return ResponseEntity.ok(creditNoteMapper.toResponse(updated));
    }

    @DeleteMapping("/api/credit-notes/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        creditNoteService.delete(principal.getUsername(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/credit-notes/{id}/issue")
    public ResponseEntity<CreditNoteResponse> issue(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        CreditNote issued = creditNoteService.issue(principal.getUsername(), id);
        return ResponseEntity.ok(creditNoteMapper.toResponse(issued));
    }

    @GetMapping("/api/credit-notes/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        CreditNotePdfService.RenderedPdf rendered = creditNotePdfService.generateForUser(principal.getUsername(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + rendered.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(rendered.bytes());
    }
}
