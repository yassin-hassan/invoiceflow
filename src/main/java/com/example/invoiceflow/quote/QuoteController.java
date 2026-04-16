package com.example.invoiceflow.quote;

import com.example.invoiceflow.quote.dto.CreateQuoteRequest;
import com.example.invoiceflow.quote.dto.QuoteResponse;
import com.example.invoiceflow.quote.dto.UpdateQuoteRequest;
import com.example.invoiceflow.quote.dto.UpdateQuoteStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final QuoteService quoteService;
    private final QuoteMapper quoteMapper;

    @GetMapping
    public ResponseEntity<List<QuoteResponse>> getQuotes(
            @AuthenticationPrincipal UserDetails principal) {
        List<QuoteResponse> quotes = quoteService.getQuotes(principal.getUsername())
                .stream()
                .map(quoteMapper::toResponse)
                .toList();
        return ResponseEntity.ok(quotes);
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuoteResponse> getQuote(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        Quote quote = quoteService.getQuote(principal.getUsername(), id);
        return ResponseEntity.ok(quoteMapper.toResponse(quote));
    }

    @PostMapping
    public ResponseEntity<QuoteResponse> createQuote(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateQuoteRequest request) {
        Quote created = quoteService.createQuote(principal.getUsername(), request);
        return ResponseEntity.status(201).body(quoteMapper.toResponse(created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuoteResponse> updateQuote(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateQuoteRequest request) {
        Quote updated = quoteService.updateQuote(principal.getUsername(), id, request);
        return ResponseEntity.ok(quoteMapper.toResponse(updated));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<QuoteResponse> updateStatus(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateQuoteStatusRequest request) {
        Quote updated = quoteService.updateStatus(principal.getUsername(), id, request);
        return ResponseEntity.ok(quoteMapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQuote(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable UUID id) {
        quoteService.deleteQuote(principal.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
