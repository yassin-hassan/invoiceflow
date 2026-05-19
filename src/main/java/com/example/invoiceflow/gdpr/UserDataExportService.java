package com.example.invoiceflow.gdpr;

import com.example.invoiceflow.audit.AuditAction;
import com.example.invoiceflow.audit.AuditLog;
import com.example.invoiceflow.audit.AuditLogRepository;
import com.example.invoiceflow.audit.AuditLogService;
import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.config.I18nConfig;
import com.example.invoiceflow.creditnote.CreditNote;
import com.example.invoiceflow.creditnote.CreditNoteRepository;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.pdf.CreditNotePdfService;
import com.example.invoiceflow.pdf.InvoicePdfService;
import com.example.invoiceflow.pdf.QuotePdfService;
import com.example.invoiceflow.product.Product;
import com.example.invoiceflow.product.ProductRepository;
import com.example.invoiceflow.quote.Quote;
import com.example.invoiceflow.quote.QuoteRepository;
import com.example.invoiceflow.user.Address;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.example.invoiceflow.gdpr.UserDataExportDtos.AddressDto;
import com.example.invoiceflow.gdpr.UserDataExportDtos.AuditLogDto;
import com.example.invoiceflow.gdpr.UserDataExportDtos.ClientDto;
import com.example.invoiceflow.gdpr.UserDataExportDtos.CreditNoteDto;
import com.example.invoiceflow.gdpr.UserDataExportDtos.CreditNoteLineDto;
import com.example.invoiceflow.gdpr.UserDataExportDtos.InvoiceDto;
import com.example.invoiceflow.gdpr.UserDataExportDtos.InvoiceLineDto;
import com.example.invoiceflow.gdpr.UserDataExportDtos.PaymentDto;
import com.example.invoiceflow.gdpr.UserDataExportDtos.ProductDto;
import com.example.invoiceflow.gdpr.UserDataExportDtos.ProfileDto;
import com.example.invoiceflow.gdpr.UserDataExportDtos.QuoteDto;
import com.example.invoiceflow.gdpr.UserDataExportDtos.QuoteLineDto;

@Service
@RequiredArgsConstructor
public class UserDataExportService {

    private final UserService userService;
    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;
    private final QuoteRepository quoteRepository;
    private final CreditNoteRepository creditNoteRepository;
    private final ProductRepository productRepository;
    private final AuditLogRepository auditLogRepository;
    private final InvoicePdfService invoicePdfService;
    private final QuotePdfService quotePdfService;
    private final CreditNotePdfService creditNotePdfService;
    private final AuditLogService auditLogService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Transactional
    public byte[] exportAllData(String email) {
        User user = userService.getByEmail(email);
        Locale locale = I18nConfig.toLocale(user.getPreferredLanguage());

        List<Client> clients = clientRepository.findByUser(user);
        List<Invoice> invoices = invoiceRepository.findByUserOrderByCreatedAtDesc(user);
        List<Quote> quotes = quoteRepository.findByUserOrderByCreatedAtDesc(user);
        List<CreditNote> creditNotes = creditNoteRepository.findByUserOrderByCreatedAtDesc(user);
        List<Product> products = productRepository.findByUser(user);
        List<AuditLog> auditLogs = auditLogRepository.findByActorEmailOrderByOccurredAtDesc(user.getEmail());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            writeText(zip, "README.txt", readme(user));
            writeJson(zip, "profile.json", toProfileDto(user));
            writeJson(zip, "clients.json", clients.stream().map(this::toClientDto).toList());
            writeJson(zip, "invoices.json", invoices.stream().map(this::toInvoiceDto).toList());
            writeJson(zip, "quotes.json", quotes.stream().map(this::toQuoteDto).toList());
            writeJson(zip, "credit-notes.json", creditNotes.stream().map(this::toCreditNoteDto).toList());
            writeJson(zip, "products.json", products.stream().map(this::toProductDto).toList());
            writeJson(zip, "audit-log.json", auditLogs.stream().map(this::toAuditLogDto).toList());

            for (Invoice inv : invoices) {
                writeBytes(zip, "pdfs/invoices/" + pdfFilename(inv.getNumber(), inv.getId()) + ".pdf",
                        invoicePdfService.generate(inv, locale));
            }
            for (Quote q : quotes) {
                writeBytes(zip, "pdfs/quotes/" + pdfFilename(q.getNumber(), q.getId()) + ".pdf",
                        quotePdfService.generate(q, locale));
            }
            for (CreditNote cn : creditNotes) {
                writeBytes(zip, "pdfs/credit-notes/" + pdfFilename(cn.getNumber(), cn.getId()) + ".pdf",
                        creditNotePdfService.generate(cn, locale));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build user data export zip", ex);
        }

        auditLogService.recordForEmail(AuditAction.USER_DATA_EXPORTED, user.getEmail(),
                "User", user.getId().toString(),
                Map.of("invoices", invoices.size(),
                        "quotes", quotes.size(),
                        "creditNotes", creditNotes.size(),
                        "clients", clients.size()));

        return out.toByteArray();
    }

    private void writeJson(ZipOutputStream zip, String name, Object value) throws IOException {
        byte[] bytes = objectMapper.writeValueAsBytes(value);
        writeBytes(zip, name, bytes);
    }

    private void writeText(ZipOutputStream zip, String name, String content) throws IOException {
        writeBytes(zip, name, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private void writeBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private String pdfFilename(String number, UUID id) {
        String safe = number != null && !number.isBlank() ? number : "draft-" + id;
        return safe.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String readme(User user) {
        return """
                InvoiceFlow — your data export
                ================================

                This archive contains all personal data InvoiceFlow holds about your
                account (%s), provided in accordance with Article 20 of the EU GDPR
                (right to data portability).

                Files
                -----
                README.txt           This file.
                profile.json         Your account profile (name, email, address, etc.).
                clients.json         Every client you have created (including inactive).
                invoices.json        Your invoices, with their lines and recorded payments.
                quotes.json          Your quotes, with their lines.
                credit-notes.json    Your credit notes, with their lines.
                products.json        Your product/service catalogue (including inactive).
                audit-log.json       Security-relevant events tied to your account
                                     (logins, password changes, 2FA events, etc.).
                pdfs/                Rendered PDF copies of every invoice, quote and
                                     credit note you have generated, organised by type.

                Notes
                -----
                - All JSON files use UTF-8 and ISO-8601 dates / date-times.
                - Monetary amounts are decimals in your account's working currency.
                - Sensitive data that we do not store in a portable form (password hash,
                  2FA secrets) is intentionally excluded.
                - If you spot anything missing or want this data removed, contact
                  support.
                """.formatted(user.getEmail());
    }

    // ----- entity → DTO mapping -----

    private ProfileDto toProfileDto(User u) {
        return new ProfileDto(
                u.getId(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                u.getCompanyName(),
                u.getPhone(),
                u.getVatNumber(),
                u.getLogoUrl(),
                toAddressDto(u.getBillingAddress()),
                u.getPreferredLanguage(),
                u.getRole() == null ? null : u.getRole().name(),
                u.isEmailVerified(),
                u.is2faEnabled(),
                u.getTwoFaPhone(),
                u.getCreatedAt(),
                u.getLastLoginAt()
        );
    }

    private AddressDto toAddressDto(Address a) {
        if (a == null) return null;
        return new AddressDto(a.getStreet(), a.getPostalCode(), a.getCity(), a.getCountry());
    }

    private ClientDto toClientDto(Client c) {
        return new ClientDto(
                c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getVatNumber(),
                toAddressDto(c.getBillingAddress()), c.getNotes(), c.isActive(), c.getCreatedAt()
        );
    }

    private InvoiceDto toInvoiceDto(Invoice i) {
        List<InvoiceLineDto> lines = i.getLines().stream()
                .sorted(Comparator.comparingInt(l -> l.getSortOrder()))
                .map(l -> new InvoiceLineDto(l.getId(), l.getDescription(), l.getQuantity(),
                        l.getUnitPrice(), l.getVatRate(), l.getSortOrder()))
                .toList();
        List<PaymentDto> payments = i.getPayments().stream()
                .sorted(Comparator.comparing(p -> p.getPaidAt()))
                .map(p -> new PaymentDto(p.getId(), p.getAmount(), p.getMethod(),
                        p.getPaidAt(), p.getNotes(), p.getCreatedAt()))
                .toList();
        return new InvoiceDto(
                i.getId(), i.getNumber(),
                i.getStatus() == null ? null : i.getStatus().name(),
                i.getClient() == null ? null : i.getClient().getId(),
                i.getClient() == null ? null : i.getClient().getName(),
                i.getQuote() == null ? null : i.getQuote().getId(),
                i.getIssueDate(), i.getDueDate(), i.getPaymentTerms(),
                i.getSentAt(), i.getCreatedAt(),
                lines, payments
        );
    }

    private QuoteDto toQuoteDto(Quote q) {
        List<QuoteLineDto> lines = q.getLines().stream()
                .sorted(Comparator.comparingInt(l -> l.getSortOrder()))
                .map(l -> new QuoteLineDto(l.getId(), l.getDescription(), l.getQuantity(),
                        l.getUnitPrice(), l.getVatRate(), l.getSortOrder()))
                .toList();
        return new QuoteDto(
                q.getId(), q.getNumber(),
                q.getStatus() == null ? null : q.getStatus().name(),
                q.getClient() == null ? null : q.getClient().getId(),
                q.getClient() == null ? null : q.getClient().getName(),
                q.getIssueDate(), q.getExpiryDate(), q.getNotes(), q.getCreatedAt(),
                lines
        );
    }

    private CreditNoteDto toCreditNoteDto(CreditNote cn) {
        List<CreditNoteLineDto> lines = cn.getLines().stream()
                .sorted(Comparator.comparingInt(l -> l.getSortOrder()))
                .map(l -> new CreditNoteLineDto(
                        l.getId(),
                        l.getInvoiceLine() == null ? null : l.getInvoiceLine().getId(),
                        l.getQuantity(), l.getSortOrder()))
                .toList();
        return new CreditNoteDto(
                cn.getId(), cn.getNumber(),
                cn.getStatus() == null ? null : cn.getStatus().name(),
                cn.getOriginalInvoice() == null ? null : cn.getOriginalInvoice().getId(),
                cn.getOriginalInvoice() == null ? null : cn.getOriginalInvoice().getNumber(),
                cn.getIssueDate(), cn.getReason(), cn.getIssuedAt(), cn.getCreatedAt(),
                lines
        );
    }

    private ProductDto toProductDto(Product p) {
        return new ProductDto(
                p.getId(), p.getName(), p.getDescription(), p.getReference(),
                p.getUnitPrice(), p.getVatRate(), p.getUnit(), p.isActive(), p.getCreatedAt()
        );
    }

    private AuditLogDto toAuditLogDto(AuditLog a) {
        return new AuditLogDto(
                a.getId(), a.getOccurredAt(),
                a.getAction() == null ? null : a.getAction().name(),
                a.getResourceType(), a.getResourceId(),
                a.getIpAddress(), a.getUserAgent(), a.getDetails()
        );
    }
}
