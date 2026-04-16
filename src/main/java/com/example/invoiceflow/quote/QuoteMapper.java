package com.example.invoiceflow.quote;

import com.example.invoiceflow.quote.dto.QuoteLineResponse;
import com.example.invoiceflow.quote.dto.QuoteResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class QuoteMapper {

    public QuoteResponse toResponse(Quote quote) {
        QuoteResponse response = new QuoteResponse();
        response.setId(quote.getId());
        response.setNumber(quote.getNumber());
        response.setStatus(quote.getStatus());
        response.setClientId(quote.getClient().getId());
        response.setClientName(quote.getClient().getName());
        response.setIssueDate(quote.getIssueDate());
        response.setExpiryDate(quote.getExpiryDate());
        response.setNotes(quote.getNotes());
        response.setCreatedAt(quote.getCreatedAt());

        List<QuoteLineResponse> lineResponses = quote.getLines().stream()
                .map(this::toLineResponse)
                .toList();
        response.setLines(lineResponses);

        BigDecimal subtotal = lineResponses.stream()
                .map(QuoteLineResponse::getTotalExclVat)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVat = lineResponses.stream()
                .map(QuoteLineResponse::getTotalVat)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        response.setSubtotalExclVat(subtotal.setScale(2, RoundingMode.HALF_UP));
        response.setTotalVat(totalVat.setScale(2, RoundingMode.HALF_UP));
        response.setTotalInclVat(subtotal.add(totalVat).setScale(2, RoundingMode.HALF_UP));

        return response;
    }

    private QuoteLineResponse toLineResponse(QuoteLine line) {
        QuoteLineResponse response = new QuoteLineResponse();
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
}
