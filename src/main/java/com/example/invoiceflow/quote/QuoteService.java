package com.example.invoiceflow.quote;

import com.example.invoiceflow.audit.AuditAction;
import com.example.invoiceflow.audit.AuditLogService;
import com.example.invoiceflow.auth.EmailService;
import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.config.I18nConfig;
import com.example.invoiceflow.exception.ResourceNotFoundException;
import com.example.invoiceflow.pdf.QuotePdfService;
import com.example.invoiceflow.product.Product;
import com.example.invoiceflow.product.ProductRepository;
import com.example.invoiceflow.quote.dto.CreateQuoteRequest;
import com.example.invoiceflow.quote.dto.QuoteLineRequest;
import com.example.invoiceflow.quote.dto.UpdateQuoteRequest;
import com.example.invoiceflow.quote.dto.UpdateQuoteStatusRequest;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteRepository quoteRepository;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final UserService userService;
    private final EmailService emailService;
    private final MessageSource messageSource;
    private final AuditLogService auditLogService;
    @Lazy
    private final QuotePdfService quotePdfService;

    public List<Quote> getQuotes(String email) {
        User user = userService.getByEmail(email);
        return quoteRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public Quote getQuote(String email, UUID quoteId) {
        User user = userService.getByEmail(email);
        return quoteRepository.findByIdAndUser(quoteId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found"));
    }

    @Transactional
    public Quote createQuote(String email, CreateQuoteRequest request) {
        User user = userService.getByEmail(email);

        Client client = clientRepository.findByIdAndUser(request.getClientId(), user)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        LocalDate issueDate = request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now();
        LocalDate expiryDate = request.getExpiryDate() != null ? request.getExpiryDate() : issueDate.plusDays(30);

        Quote quote = new Quote();
        quote.setUser(user);
        quote.setClient(client);
        quote.setNumber(generateQuoteNumber(user, issueDate));
        quote.setIssueDate(issueDate);
        quote.setExpiryDate(expiryDate);
        quote.setNotes(request.getNotes());

        addLinesToQuote(quote, request.getLines(), user);

        return quoteRepository.save(quote);
    }

    @Transactional
    public Quote updateQuote(String email, UUID quoteId, UpdateQuoteRequest request) {
        User user = userService.getByEmail(email);
        Quote quote = quoteRepository.findByIdAndUser(quoteId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found"));

        if (quote.getStatus() != QuoteStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT quotes can be edited");
        }

        if (request.getClientId() != null) {
            Client client = clientRepository.findByIdAndUser(request.getClientId(), user)
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
            quote.setClient(client);
        }

        if (request.getIssueDate() != null) quote.setIssueDate(request.getIssueDate());
        if (request.getExpiryDate() != null) quote.setExpiryDate(request.getExpiryDate());
        if (request.getNotes() != null) quote.setNotes(request.getNotes());

        if (request.getLines() != null) {
            quote.getLines().clear();
            addLinesToQuote(quote, request.getLines(), user);
        }

        return quoteRepository.save(quote);
    }

    @Transactional
    public Quote updateStatus(String email, UUID quoteId, UpdateQuoteStatusRequest request) {
        User user = userService.getByEmail(email);
        Quote quote = quoteRepository.findByIdAndUser(quoteId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found"));

        validateTransition(quote.getStatus(), request.getStatus());
        quote.setStatus(request.getStatus());

        return quoteRepository.save(quote);
    }

    @Transactional
    public Quote sendQuote(String email, UUID quoteId) {
        User user = userService.getByEmail(email);
        Quote quote = quoteRepository.findByIdAndUser(quoteId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found"));

        if (quote.getStatus() != QuoteStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT quotes can be sent");
        }
        String recipient = quote.getClient().getEmail();
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalStateException("Client has no email address");
        }

        quote.setStatus(QuoteStatus.SENT);

        Locale locale = I18nConfig.toLocale(user.getPreferredLanguage());
        byte[] pdf = quotePdfService.generate(quote, locale);
        String filename = quote.getNumber() + ".pdf";
        String subject = buildQuoteSubject(quote, user, locale);
        String body = buildQuoteBody(quote, user, locale);
        emailService.sendInvoice(recipient, subject, body, filename, pdf);

        Quote saved = quoteRepository.save(quote);
        auditLogService.record(AuditAction.QUOTE_SENT, "Quote", saved.getId().toString(),
                Map.of("number", saved.getNumber(), "clientEmail", recipient));
        return saved;
    }

    private String buildQuoteSubject(Quote quote, User user, Locale locale) {
        String sender = (user.getCompanyName() != null && !user.getCompanyName().isBlank())
                ? user.getCompanyName()
                : (user.getFirstName() + " " + user.getLastName()).trim();
        return messageSource.getMessage("email.quote.subject",
                new Object[]{quote.getNumber(), sender}, locale);
    }

    private String buildQuoteBody(Quote quote, User user, Locale locale) {
        String sender = (user.getCompanyName() != null && !user.getCompanyName().isBlank())
                ? user.getCompanyName()
                : (user.getFirstName() + " " + user.getLastName()).trim();
        String clientName = quote.getClient().getName();
        String number = quote.getNumber();
        String validUntil = quote.getExpiryDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        BigDecimal totalInclVat = quote.getLines().stream()
                .map(l -> l.getQuantity().multiply(l.getUnitPrice())
                        .multiply(BigDecimal.ONE.add(l.getVatRate().divide(BigDecimal.valueOf(100)))))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        Locale numberLocale = "en".equalsIgnoreCase(locale.getLanguage())
                ? Locale.of("en", "IE")
                : Locale.of("fr", "BE");
        String totalStr = String.format(numberLocale, "%,.2f €", totalInclVat);

        StringBuilder html = new StringBuilder();
        html.append(messageSource.getMessage("email.quote.greeting",
                new Object[]{escapeHtml(clientName)}, locale));
        html.append(messageSource.getMessage("email.quote.intro",
                new Object[]{escapeHtml(number), totalStr, validUntil}, locale));
        html.append(messageSource.getMessage("email.quote.questions", null, locale));
        html.append(messageSource.getMessage("email.quote.signature",
                new Object[]{escapeHtml(sender)}, locale));
        return html.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @Transactional
    public void deleteQuote(String email, UUID quoteId) {
        User user = userService.getByEmail(email);
        Quote quote = quoteRepository.findByIdAndUser(quoteId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found"));

        if (quote.getStatus() != QuoteStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT quotes can be deleted");
        }

        quoteRepository.delete(quote);
    }

    private void addLinesToQuote(Quote quote, List<QuoteLineRequest> lineRequests, User user) {
        for (QuoteLineRequest lineRequest : lineRequests) {
            QuoteLine line = new QuoteLine();
            line.setQuote(quote);
            line.setDescription(lineRequest.getDescription());
            line.setQuantity(lineRequest.getQuantity());
            line.setUnitPrice(lineRequest.getUnitPrice());
            line.setVatRate(lineRequest.getVatRate());
            line.setSortOrder(lineRequest.getSortOrder());

            if (lineRequest.getProductId() != null) {
                Product product = productRepository.findByIdAndUser(lineRequest.getProductId(), user)
                        .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + lineRequest.getProductId()));
                line.setProduct(product);
            }

            quote.getLines().add(line);
        }
    }

    private void validateTransition(QuoteStatus current, QuoteStatus next) {
        boolean valid = switch (current) {
            case DRAFT -> next == QuoteStatus.SENT;
            case SENT -> next == QuoteStatus.ACCEPTED || next == QuoteStatus.REJECTED;
            case ACCEPTED -> next == QuoteStatus.CONVERTED;
            case REJECTED, CONVERTED -> false;
        };

        if (!valid) {
            throw new IllegalStateException(
                    "Cannot transition from " + current + " to " + next);
        }
    }

    private String generateQuoteNumber(User user, LocalDate issueDate) {
        int year = issueDate.getYear();
        long count = quoteRepository.countByUserAndYear(user, year);
        return String.format("DEV-%d-%03d", year, count + 1);
    }
}
