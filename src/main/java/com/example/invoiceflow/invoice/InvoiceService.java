package com.example.invoiceflow.invoice;

import com.example.invoiceflow.auth.EmailService;
import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.exception.ResourceNotFoundException;
import com.example.invoiceflow.invoice.dto.CreateInvoiceRequest;
import com.example.invoiceflow.invoice.dto.InvoiceLineRequest;
import com.example.invoiceflow.invoice.dto.RecordPaymentRequest;
import com.example.invoiceflow.invoice.dto.UpdateInvoiceRequest;
import com.example.invoiceflow.invoice.dto.UpdateInvoiceStatusRequest;
import com.example.invoiceflow.pdf.InvoicePdfService;
import com.example.invoiceflow.product.Product;
import com.example.invoiceflow.product.ProductRepository;
import com.example.invoiceflow.quote.Quote;
import com.example.invoiceflow.quote.QuoteRepository;
import com.example.invoiceflow.quote.QuoteStatus;
import com.example.invoiceflow.stripe.StripeService;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserService;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.Map;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final QuoteRepository quoteRepository;
    private final PaymentRepository paymentRepository;
    private final UserService userService;
    private final EmailService emailService;
    private final StripeService stripeService;
    @Lazy
    private final InvoicePdfService invoicePdfService;

    public List<Invoice> getInvoices(String email) {
        User user = userService.getByEmail(email);
        return invoiceRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public Invoice getInvoice(String email, UUID invoiceId) {
        User user = userService.getByEmail(email);
        return invoiceRepository.findByIdAndUser(invoiceId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
    }

    @Transactional
    public Invoice createInvoice(String email, CreateInvoiceRequest request) {
        User user = userService.getByEmail(email);

        Client client = clientRepository.findByIdAndUser(request.getClientId(), user)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found"));

        LocalDate issueDate = request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now();
        LocalDate dueDate = request.getDueDate() != null ? request.getDueDate() : issueDate.plusDays(30);

        Invoice invoice = new Invoice();
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setIssueDate(issueDate);
        invoice.setDueDate(dueDate);
        invoice.setPaymentTerms(request.getPaymentTerms());

        addLinesToInvoice(invoice, request.getLines(), user);

        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice convertFromQuote(String email, UUID quoteId) {
        User user = userService.getByEmail(email);

        Quote quote = quoteRepository.findByIdAndUser(quoteId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found"));

        if (quote.getStatus() != QuoteStatus.ACCEPTED) {
            throw new IllegalStateException("Only ACCEPTED quotes can be converted to invoices");
        }

        LocalDate issueDate = LocalDate.now();
        LocalDate dueDate = issueDate.plusDays(30);

        Invoice invoice = new Invoice();
        invoice.setUser(user);
        invoice.setClient(quote.getClient());
        invoice.setQuote(quote);
        invoice.setIssueDate(issueDate);
        invoice.setDueDate(dueDate);

        for (var quoteLine : quote.getLines()) {
            InvoiceLine line = new InvoiceLine();
            line.setInvoice(invoice);
            line.setProduct(quoteLine.getProduct());
            line.setDescription(quoteLine.getDescription());
            line.setQuantity(quoteLine.getQuantity());
            line.setUnitPrice(quoteLine.getUnitPrice());
            line.setVatRate(quoteLine.getVatRate());
            line.setSortOrder(quoteLine.getSortOrder());
            invoice.getLines().add(line);
        }

        Invoice saved = invoiceRepository.save(invoice);

        quote.setStatus(QuoteStatus.CONVERTED);
        quoteRepository.save(quote);

        return saved;
    }

    @Transactional
    public Invoice updateInvoice(String email, UUID invoiceId, UpdateInvoiceRequest request) {
        User user = userService.getByEmail(email);
        Invoice invoice = invoiceRepository.findByIdAndUser(invoiceId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT invoices can be edited");
        }

        if (request.getClientId() != null) {
            Client client = clientRepository.findByIdAndUser(request.getClientId(), user)
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
            invoice.setClient(client);
        }

        if (request.getIssueDate() != null) invoice.setIssueDate(request.getIssueDate());
        if (request.getDueDate() != null) invoice.setDueDate(request.getDueDate());
        if (request.getPaymentTerms() != null) invoice.setPaymentTerms(request.getPaymentTerms());

        if (request.getLines() != null) {
            invoice.getLines().clear();
            addLinesToInvoice(invoice, request.getLines(), user);
        }

        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice updateStatus(String email, UUID invoiceId, UpdateInvoiceStatusRequest request) {
        User user = userService.getByEmail(email);
        Invoice invoice = invoiceRepository.findByIdAndUser(invoiceId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        validateTransition(invoice.getStatus(), request.getStatus());

        if (request.getStatus() == InvoiceStatus.SENT && invoice.getNumber() == null) {
            return assignNumberAndStatus(invoice, InvoiceStatus.SENT);
        }

        invoice.setStatus(request.getStatus());
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice sendInvoice(String email, UUID invoiceId) {
        User user = userService.getByEmail(email);
        Invoice invoice = invoiceRepository.findByIdAndUser(invoiceId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT invoices can be sent");
        }
        String recipient = invoice.getClient().getEmail();
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalStateException("Client has no email address");
        }

        Invoice issued = assignNumberAndStatus(invoice, InvoiceStatus.SENT);
        issued.setSentAt(LocalDateTime.now());

        tryAttachPaymentLink(issued, user);

        byte[] pdf = invoicePdfService.generate(issued);
        String filename = issued.getNumber() + ".pdf";
        String subject = buildInvoiceSubject(issued, user);
        String body = buildInvoiceBody(issued, user);
        emailService.sendInvoice(recipient, subject, body, filename, pdf);

        return invoiceRepository.save(issued);
    }

    private void tryAttachPaymentLink(Invoice invoice, User user) {
        BigDecimal netAmount = computeTotalInclVat(invoice);
        if (netAmount.compareTo(BigDecimal.ZERO) <= 0) return;

        String description = "Facture " + invoice.getNumber();
        Map<String, String> metadata = Map.of(
                "invoiceId", invoice.getId().toString(),
                "userId", user.getId().toString()
        );
        try {
            PaymentLink link = stripeService.createPaymentLink(netAmount, description, metadata);
            invoice.setStripePaymentLinkId(link.getId());
            invoice.setStripePaymentLinkUrl(link.getUrl());
            invoice.setStripePaymentLinkCreatedAt(LocalDateTime.now());
        } catch (StripeException | RuntimeException e) {
            log.warn("Stripe payment link generation failed for invoice {}: {}",
                    invoice.getId(), e.getMessage());
        }
    }

    private BigDecimal computeTotalInclVat(Invoice invoice) {
        return invoice.getLines().stream()
                .map(l -> {
                    BigDecimal excl = l.getQuantity().multiply(l.getUnitPrice());
                    return excl.add(excl.multiply(l.getVatRate())
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String buildInvoiceSubject(Invoice invoice, User user) {
        String sender = (user.getCompanyName() != null && !user.getCompanyName().isBlank())
                ? user.getCompanyName()
                : (user.getFirstName() + " " + user.getLastName()).trim();
        return "Facture " + invoice.getNumber() + " — " + sender;
    }

    private String buildInvoiceBody(Invoice invoice, User user) {
        String sender = (user.getCompanyName() != null && !user.getCompanyName().isBlank())
                ? user.getCompanyName()
                : (user.getFirstName() + " " + user.getLastName()).trim();
        String clientName = invoice.getClient().getName();
        String number = invoice.getNumber();
        String dueDate = invoice.getDueDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        BigDecimal totalInclVat = invoice.getLines().stream()
                .map(l -> l.getQuantity().multiply(l.getUnitPrice())
                        .multiply(BigDecimal.ONE.add(l.getVatRate().divide(BigDecimal.valueOf(100)))))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        String totalStr = String.format(java.util.Locale.of("fr", "BE"), "%,.2f €", totalInclVat);

        StringBuilder html = new StringBuilder();
        html.append("<p>Bonjour ").append(escapeHtml(clientName)).append(",</p>");
        html.append("<p>Veuillez trouver ci-joint la facture <strong>").append(escapeHtml(number))
                .append("</strong> d'un montant de <strong>").append(totalStr)
                .append("</strong> TVAC, à régler avant le <strong>").append(dueDate).append("</strong>.</p>");
        if (invoice.getPaymentTerms() != null && !invoice.getPaymentTerms().isBlank()) {
            html.append("<p>").append(escapeHtml(invoice.getPaymentTerms())).append("</p>");
        }
        if (invoice.getStripePaymentLinkUrl() != null) {
            html.append("<p style=\"margin-top:24px;\"><strong>Payer en ligne</strong><br>")
                    .append("<a href=\"").append(escapeHtml(invoice.getStripePaymentLinkUrl()))
                    .append("\">Régler cette facture — ").append(totalStr).append("</a><br>")
                    .append("<span style=\"color:#666; font-size:0.9em;\">Bancontact, virement SEPA ou carte bancaire — paiement instantané.</span></p>");
        }
        html.append("<p>N'hésitez pas à me contacter pour toute question.</p>");
        html.append("<p>Cordialement,<br>").append(escapeHtml(sender)).append("</p>");
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
    public void deleteInvoice(String email, UUID invoiceId) {
        User user = userService.getByEmail(email);
        Invoice invoice = invoiceRepository.findByIdAndUser(invoiceId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT invoices can be deleted");
        }

        invoiceRepository.delete(invoice);
    }

    @Transactional
    public Invoice recordPayment(String email, UUID invoiceId, RecordPaymentRequest request) {
        User user = userService.getByEmail(email);
        Invoice invoice = invoiceRepository.findByIdAndUser(invoiceId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        if (invoice.getStatus() == InvoiceStatus.PAID || invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalStateException("Cannot record payment on a " + invoice.getStatus() + " invoice");
        }

        BigDecimal totalInclVat = invoice.getLines().stream()
                .map(l -> l.getQuantity().multiply(l.getUnitPrice())
                        .multiply(BigDecimal.ONE.add(l.getVatRate().divide(BigDecimal.valueOf(100)))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal alreadyPaid = invoice.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal amountDue = totalInclVat.subtract(alreadyPaid);

        if (request.getAmount().compareTo(amountDue) > 0) {
            throw new IllegalArgumentException("Payment amount exceeds the remaining balance of " + amountDue);
        }

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(request.getAmount());
        payment.setMethod(request.getMethod());
        payment.setPaidAt(request.getPaidAt());
        payment.setNotes(request.getNotes());
        invoice.getPayments().add(payment);

        BigDecimal newAmountPaid = alreadyPaid.add(request.getAmount());
        if (newAmountPaid.compareTo(totalInclVat) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }

        return invoiceRepository.save(invoice);
    }

    private void addLinesToInvoice(Invoice invoice, List<InvoiceLineRequest> lineRequests, User user) {
        for (InvoiceLineRequest lineRequest : lineRequests) {
            InvoiceLine line = new InvoiceLine();
            line.setInvoice(invoice);
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

            invoice.getLines().add(line);
        }
    }

    private void validateTransition(InvoiceStatus current, InvoiceStatus next) {
        boolean valid = switch (current) {
            case DRAFT -> next == InvoiceStatus.SENT;
            case SENT -> next == InvoiceStatus.OVERDUE;
            case PARTIALLY_PAID -> next == InvoiceStatus.OVERDUE;
            case OVERDUE, PAID, CANCELLED -> false;
        };

        if (!valid) {
            throw new IllegalStateException("Cannot transition from " + current + " to " + next);
        }
    }

    private Invoice assignNumberAndStatus(Invoice invoice, InvoiceStatus newStatus) {
        int year = invoice.getIssueDate().getYear();
        String prefix = String.format("FACT-%d-%%", year);
        int maxSuffix = invoiceRepository.findMaxNumberSuffixByUserAndPrefix(invoice.getUser(), prefix);
        invoice.setNumber(String.format("FACT-%d-%03d", year, maxSuffix + 1));
        invoice.setStatus(newStatus);
        return invoiceRepository.saveAndFlush(invoice);
    }
}
