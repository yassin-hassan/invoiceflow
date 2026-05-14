package com.example.invoiceflow.creditnote;

import com.example.invoiceflow.creditnote.dto.CreateCreditNoteRequest;
import com.example.invoiceflow.creditnote.dto.CreditNoteLineRequest;
import com.example.invoiceflow.creditnote.dto.UpdateCreditNoteRequest;
import com.example.invoiceflow.exception.ResourceNotFoundException;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.invoice.InvoiceLineRepository;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.InvoiceStatus;
import com.example.invoiceflow.stripe.StripeService;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditNoteService {

    private static final List<InvoiceStatus> ELIGIBLE_INVOICE_STATUSES = List.of(
            InvoiceStatus.SENT,
            InvoiceStatus.PARTIALLY_PAID,
            InvoiceStatus.PAID,
            InvoiceStatus.OVERDUE
    );

    private final CreditNoteRepository creditNoteRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final UserService userService;
    private final StripeService stripeService;

    public List<CreditNote> list(String email) {
        User user = userService.getByEmail(email);
        return creditNoteRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public CreditNote get(String email, UUID id) {
        User user = userService.getByEmail(email);
        return creditNoteRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Credit note not found"));
    }

    @Transactional
    public CreditNote create(String email, UUID invoiceId, CreateCreditNoteRequest request) {
        User user = userService.getByEmail(email);
        Invoice invoice = invoiceRepository.findByIdAndUser(invoiceId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        if (!ELIGIBLE_INVOICE_STATUSES.contains(invoice.getStatus())) {
            throw new IllegalStateException(
                    "Cannot create a credit note for an invoice in status " + invoice.getStatus());
        }

        CreditNote creditNote = new CreditNote();
        creditNote.setUser(user);
        creditNote.setOriginalInvoice(invoice);
        creditNote.setIssueDate(request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now());
        creditNote.setReason(request.getReason().trim());

        buildLines(creditNote, invoice, request.getLines());
        validateCaps(creditNote, invoice, issuedOthers(invoice));
        return creditNoteRepository.save(creditNote);
    }

    @Transactional
    public CreditNote update(String email, UUID id, UpdateCreditNoteRequest request) {
        CreditNote creditNote = get(email, id);
        if (creditNote.getStatus() != CreditNoteStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT credit notes can be edited");
        }
        Invoice invoice = creditNote.getOriginalInvoice();
        creditNote.setReason(request.getReason().trim());
        creditNote.getLines().clear();
        buildLines(creditNote, invoice, request.getLines());
        validateCaps(creditNote, invoice, issuedOthers(invoice));
        return creditNoteRepository.save(creditNote);
    }

    @Transactional
    public void delete(String email, UUID id) {
        CreditNote creditNote = get(email, id);
        if (creditNote.getStatus() != CreditNoteStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT credit notes can be deleted");
        }
        creditNoteRepository.delete(creditNote);
    }

    @Transactional
    public CreditNote issue(String email, UUID id) {
        CreditNote creditNote = get(email, id);
        if (creditNote.getStatus() != CreditNoteStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT credit notes can be issued");
        }
        Invoice invoice = creditNote.getOriginalInvoice();
        validateCaps(creditNote, invoice, issuedOthers(invoice));
        int year = creditNote.getIssueDate().getYear();
        String prefix = String.format("AV-%d-%%", year);
        int maxSuffix = creditNoteRepository.findMaxNumberSuffixByUserAndPrefix(creditNote.getUser(), prefix);
        creditNote.setNumber(String.format("AV-%d-%03d", year, maxSuffix + 1));
        creditNote.setStatus(CreditNoteStatus.ISSUED);
        creditNote.setIssuedAt(LocalDateTime.now());

        invalidateStripePaymentLink(invoice);

        return creditNoteRepository.saveAndFlush(creditNote);
    }

    private void invalidateStripePaymentLink(Invoice invoice) {
        String linkId = invoice.getStripePaymentLinkId();
        if (linkId == null) return;
        try {
            stripeService.deactivatePaymentLink(linkId);
        } catch (StripeException | RuntimeException e) {
            log.warn("Failed to deactivate Stripe payment link {} for invoice {}: {}",
                    linkId, invoice.getId(), e.getMessage());
        }
        invoice.setStripePaymentLinkId(null);
        invoice.setStripePaymentLinkUrl(null);
        invoice.setStripePaymentLinkCreatedAt(null);
        invoiceRepository.save(invoice);
    }

    private void buildLines(CreditNote creditNote, Invoice invoice, List<CreditNoteLineRequest> lineRequests) {
        Map<UUID, InvoiceLine> invoiceLineIndex = new HashMap<>();
        for (InvoiceLine line : invoice.getLines()) {
            invoiceLineIndex.put(line.getId(), line);
        }

        int autoOrder = 0;
        for (CreditNoteLineRequest lineRequest : lineRequests) {
            InvoiceLine source = invoiceLineIndex.get(lineRequest.getInvoiceLineId());
            if (source == null) {
                throw new IllegalStateException(
                        "Line " + lineRequest.getInvoiceLineId() + " does not belong to invoice " + invoice.getId());
            }
            CreditNoteLine line = new CreditNoteLine();
            line.setCreditNote(creditNote);
            line.setInvoiceLine(source);
            line.setQuantity(lineRequest.getQuantity());
            line.setSortOrder(lineRequest.getSortOrder() != null ? lineRequest.getSortOrder() : autoOrder);
            creditNote.getLines().add(line);
            autoOrder++;
        }
    }

    private List<CreditNote> issuedOthers(Invoice invoice) {
        return creditNoteRepository.findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(invoice, CreditNoteStatus.ISSUED);
    }

    private void validateCaps(CreditNote candidate, Invoice invoice, List<CreditNote> issuedOthers) {
        Map<UUID, BigDecimal> alreadyCredited = new HashMap<>();
        for (CreditNote other : issuedOthers) {
            if (other.getId() != null && other.getId().equals(candidate.getId())) continue;
            for (CreditNoteLine cl : other.getLines()) {
                alreadyCredited.merge(cl.getInvoiceLine().getId(), cl.getQuantity(), BigDecimal::add);
            }
        }

        Map<UUID, BigDecimal> originalQty = new HashMap<>();
        for (InvoiceLine il : invoice.getLines()) {
            originalQty.put(il.getId(), il.getQuantity());
        }

        Map<UUID, BigDecimal> candidateByLine = new HashMap<>();
        for (CreditNoteLine cl : candidate.getLines()) {
            candidateByLine.merge(cl.getInvoiceLine().getId(), cl.getQuantity(), BigDecimal::add);
        }

        for (Map.Entry<UUID, BigDecimal> entry : candidateByLine.entrySet()) {
            BigDecimal already = alreadyCredited.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            BigDecimal original = originalQty.get(entry.getKey());
            BigDecimal proposed = already.add(entry.getValue());
            if (proposed.compareTo(original) > 0) {
                throw new IllegalStateException(
                        "Credit quantity " + proposed
                                + " (incl. already credited " + already + ")"
                                + " exceeds original quantity " + original
                                + " on line " + entry.getKey());
            }
        }

        BigDecimal candidateTotal = creditNoteTotalInclVat(candidate);
        BigDecimal othersTotal = issuedOthers.stream()
                .filter(other -> other.getId() == null || !other.getId().equals(candidate.getId()))
                .map(this::creditNoteTotalInclVat)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal invoiceTotal = invoiceTotalInclVat(invoice);

        BigDecimal proposed = candidateTotal.add(othersTotal).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cap = invoiceTotal.setScale(2, RoundingMode.HALF_UP);
        if (proposed.compareTo(cap) > 0) {
            throw new IllegalStateException(
                    "Credit note total " + candidateTotal.setScale(2, RoundingMode.HALF_UP)
                            + " plus already issued " + othersTotal.setScale(2, RoundingMode.HALF_UP)
                            + " exceeds invoice total " + cap);
        }
    }

    private BigDecimal creditNoteTotalInclVat(CreditNote creditNote) {
        return creditNote.getLines().stream()
                .map(line -> {
                    InvoiceLine source = line.getInvoiceLine();
                    BigDecimal excl = line.getQuantity().multiply(source.getUnitPrice());
                    return excl.add(excl.multiply(source.getVatRate())
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal invoiceTotalInclVat(Invoice invoice) {
        return invoice.getLines().stream()
                .map(line -> {
                    BigDecimal exclVat = line.getQuantity().multiply(line.getUnitPrice());
                    return exclVat.add(exclVat.multiply(line.getVatRate())
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
