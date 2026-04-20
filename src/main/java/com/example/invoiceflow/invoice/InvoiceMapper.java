package com.example.invoiceflow.invoice;

import com.example.invoiceflow.invoice.dto.InvoiceLineResponse;
import com.example.invoiceflow.invoice.dto.InvoiceResponse;
import com.example.invoiceflow.invoice.dto.PaymentResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class InvoiceMapper {

    public InvoiceResponse toResponse(Invoice invoice) {
        InvoiceResponse response = new InvoiceResponse();
        response.setId(invoice.getId());
        response.setNumber(invoice.getNumber());
        response.setStatus(invoice.getStatus());
        response.setClientId(invoice.getClient().getId());
        response.setClientName(invoice.getClient().getName());
        response.setQuoteId(invoice.getQuote() != null ? invoice.getQuote().getId() : null);
        response.setIssueDate(invoice.getIssueDate());
        response.setDueDate(invoice.getDueDate());
        response.setPaymentTerms(invoice.getPaymentTerms());
        response.setCreatedAt(invoice.getCreatedAt());

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
