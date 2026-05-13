package com.example.invoiceflow.invoice;

import com.example.invoiceflow.creditnote.CreditNote;
import com.example.invoiceflow.creditnote.CreditNoteLine;
import com.example.invoiceflow.creditnote.CreditNoteRepository;
import com.example.invoiceflow.creditnote.CreditNoteStatus;
import com.example.invoiceflow.invoice.dto.CreditNoteSummary;
import com.example.invoiceflow.invoice.dto.InvoiceLineResponse;
import com.example.invoiceflow.invoice.dto.InvoiceResponse;
import com.example.invoiceflow.invoice.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
@RequiredArgsConstructor
public class InvoiceMapper {

    @Lazy
    private final CreditNoteRepository creditNoteRepository;

    public InvoiceResponse toResponse(Invoice invoice) {
        InvoiceResponse response = new InvoiceResponse();
        response.setId(invoice.getId());
        response.setNumber(invoice.getNumber());
        response.setStatus(invoice.getStatus());
        response.setClientId(invoice.getClient().getId());
        response.setClientName(invoice.getClient().getName());
        response.setClientEmail(invoice.getClient().getEmail());
        response.setQuoteId(invoice.getQuote() != null ? invoice.getQuote().getId() : null);
        response.setIssueDate(invoice.getIssueDate());
        response.setDueDate(invoice.getDueDate());
        response.setPaymentTerms(invoice.getPaymentTerms());
        response.setCreatedAt(invoice.getCreatedAt());
        response.setSentAt(invoice.getSentAt());

        List<CreditNote> creditNotes = creditNoteRepository.findAllByOriginalInvoiceOrderByCreatedAtAsc(invoice);
        List<CreditNoteSummary> summaries = creditNotes.stream()
                .map(this::toCreditNoteSummary)
                .toList();
        response.setCreditNotes(summaries);

        BigDecimal issuedTotal = summaries.stream()
                .filter(s -> s.getStatus() == CreditNoteStatus.ISSUED)
                .map(CreditNoteSummary::getTotalInclVat)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        response.setCreditNoteTotalInclVat(issuedTotal.compareTo(BigDecimal.ZERO) > 0 ? issuedTotal : null);

        List<InvoiceLineResponse> lineResponses = invoice.getLines().stream()
                .sorted(java.util.Comparator.comparingInt(InvoiceLine::getSortOrder))
                .map(this::toLineResponse)
                .toList();
        response.setLines(lineResponses);

        List<PaymentResponse> paymentResponses = invoice.getPayments().stream()
                .sorted(java.util.Comparator.comparing(Payment::getPaidAt))
                .map(this::toPaymentResponse)
                .toList();
        response.setPayments(paymentResponses);

        BigDecimal subtotal = lineResponses.stream()
                .map(InvoiceLineResponse::getTotalExclVat)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVat = lineResponses.stream()
                .map(InvoiceLineResponse::getTotalVat)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInclVat = subtotal.add(totalVat).setScale(2, RoundingMode.HALF_UP);

        BigDecimal amountPaid = invoice.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        response.setSubtotalExclVat(subtotal.setScale(2, RoundingMode.HALF_UP));
        response.setTotalVat(totalVat.setScale(2, RoundingMode.HALF_UP));
        response.setTotalInclVat(totalInclVat);
        response.setAmountPaid(amountPaid.setScale(2, RoundingMode.HALF_UP));
        response.setAmountDue(totalInclVat.subtract(amountPaid).setScale(2, RoundingMode.HALF_UP));

        return response;
    }

    private InvoiceLineResponse toLineResponse(InvoiceLine line) {
        InvoiceLineResponse response = new InvoiceLineResponse();
        response.setId(line.getId());
        response.setProductId(line.getProduct() != null ? line.getProduct().getId() : null);
        response.setDescription(line.getDescription());
        response.setQuantity(line.getQuantity());
        response.setUnitPrice(line.getUnitPrice());
        response.setVatRate(line.getVatRate());
        response.setSortOrder(line.getSortOrder());

        BigDecimal totalExclVat = line.getQuantity().multiply(line.getUnitPrice())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalVat = totalExclVat.multiply(line.getVatRate())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        response.setTotalExclVat(totalExclVat);
        response.setTotalVat(totalVat);
        response.setTotalInclVat(totalExclVat.add(totalVat));

        return response;
    }

    private CreditNoteSummary toCreditNoteSummary(CreditNote creditNote) {
        CreditNoteSummary summary = new CreditNoteSummary();
        summary.setId(creditNote.getId());
        summary.setNumber(creditNote.getNumber());
        summary.setStatus(creditNote.getStatus());
        summary.setIssueDate(creditNote.getIssueDate());

        BigDecimal total = BigDecimal.ZERO;
        List<CreditNoteSummary.CreditNoteLineSummary> lineSummaries = new java.util.ArrayList<>();
        for (CreditNoteLine line : creditNote.getLines()) {
            InvoiceLine source = line.getInvoiceLine();
            BigDecimal excl = line.getQuantity().multiply(source.getUnitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal vat = excl.multiply(source.getVatRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            total = total.add(excl).add(vat);

            CreditNoteSummary.CreditNoteLineSummary lineSummary = new CreditNoteSummary.CreditNoteLineSummary();
            lineSummary.setInvoiceLineId(source.getId());
            lineSummary.setQuantity(line.getQuantity());
            lineSummaries.add(lineSummary);
        }
        summary.setLines(lineSummaries);
        summary.setTotalInclVat(total.setScale(2, RoundingMode.HALF_UP));
        return summary;
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId());
        response.setAmount(payment.getAmount());
        response.setMethod(payment.getMethod());
        response.setPaidAt(payment.getPaidAt());
        response.setNotes(payment.getNotes());
        response.setCreatedAt(payment.getCreatedAt());
        return response;
    }
}
