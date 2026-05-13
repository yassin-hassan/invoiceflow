package com.example.invoiceflow.creditnote;

import com.example.invoiceflow.creditnote.dto.CreditNoteLineResponse;
import com.example.invoiceflow.creditnote.dto.CreditNoteResponse;
import com.example.invoiceflow.invoice.InvoiceLine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Component
public class CreditNoteMapper {

    public CreditNoteResponse toResponse(CreditNote creditNote) {
        CreditNoteResponse response = new CreditNoteResponse();
        response.setId(creditNote.getId());
        response.setNumber(creditNote.getNumber());
        response.setStatus(creditNote.getStatus());
        response.setOriginalInvoiceId(creditNote.getOriginalInvoice().getId());
        response.setOriginalInvoiceNumber(creditNote.getOriginalInvoice().getNumber());
        response.setClientId(creditNote.getOriginalInvoice().getClient().getId());
        response.setClientName(creditNote.getOriginalInvoice().getClient().getName());
        response.setIssueDate(creditNote.getIssueDate());
        response.setReason(creditNote.getReason());
        response.setCreatedAt(creditNote.getCreatedAt());
        response.setIssuedAt(creditNote.getIssuedAt());

        List<CreditNoteLineResponse> lineResponses = creditNote.getLines().stream()
                .sorted(Comparator.comparingInt(CreditNoteLine::getSortOrder))
                .map(this::toLineResponse)
                .toList();
        response.setLines(lineResponses);

        BigDecimal subtotal = lineResponses.stream()
                .map(CreditNoteLineResponse::getTotalExclVat)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVat = lineResponses.stream()
                .map(CreditNoteLineResponse::getTotalVat)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInclVat = subtotal.add(totalVat).setScale(2, RoundingMode.HALF_UP);

        response.setSubtotalExclVat(subtotal.setScale(2, RoundingMode.HALF_UP));
        response.setTotalVat(totalVat.setScale(2, RoundingMode.HALF_UP));
        response.setTotalInclVat(totalInclVat);

        return response;
    }

    private CreditNoteLineResponse toLineResponse(CreditNoteLine line) {
        InvoiceLine source = line.getInvoiceLine();
        CreditNoteLineResponse response = new CreditNoteLineResponse();
        response.setId(line.getId());
        response.setInvoiceLineId(source.getId());
        response.setDescription(source.getDescription());
        response.setUnitPrice(source.getUnitPrice());
        response.setVatRate(source.getVatRate());
        response.setQuantity(line.getQuantity());
        response.setSortOrder(line.getSortOrder());

        BigDecimal totalExclVat = line.getQuantity().multiply(source.getUnitPrice())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalVat = totalExclVat.multiply(source.getVatRate())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        response.setTotalExclVat(totalExclVat);
        response.setTotalVat(totalVat);
        response.setTotalInclVat(totalExclVat.add(totalVat));

        return response;
    }
}
