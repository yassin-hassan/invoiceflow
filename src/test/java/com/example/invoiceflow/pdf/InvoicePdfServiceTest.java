package com.example.invoiceflow.pdf;

import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.invoice.InvoiceStatus;
import com.example.invoiceflow.user.Address;
import com.example.invoiceflow.user.User;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InvoicePdfServiceTest {

    private final InvoicePdfService service = new InvoicePdfService(null);

    private User user;
    private Client client;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        Address userAddress = new Address();
        userAddress.setStreet("Rue de la Loi 16");
        userAddress.setPostalCode("1000");
        userAddress.setCity("Bruxelles");
        userAddress.setCountry("Belgique");

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("freelance@example.com");
        user.setCompanyName("Acme Consulting");
        user.setVatNumber("BE0123456789");
        user.setBillingAddress(userAddress);

        Address clientAddress = new Address();
        clientAddress.setStreet("Avenue Louise 100");
        clientAddress.setPostalCode("1050");
        clientAddress.setCity("Ixelles");
        clientAddress.setCountry("Belgique");

        client = new Client();
        client.setId(UUID.randomUUID());
        client.setName("Globex Industries");
        client.setEmail("billing@globex.example");
        client.setVatNumber("BE0987654321");
        client.setBillingAddress(clientAddress);

        InvoiceLine line = new InvoiceLine();
        line.setDescription("Consultance backend");
        line.setQuantity(new BigDecimal("10"));
        line.setUnitPrice(new BigDecimal("120.00"));
        line.setVatRate(new BigDecimal("21.00"));
        line.setSortOrder(0);

        invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setNumber("FACT-2026-001");
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setIssueDate(LocalDate.of(2026, 4, 1));
        invoice.setDueDate(LocalDate.of(2026, 5, 1));
        invoice.setPaymentTerms("Paiement à 30 jours par virement bancaire.");
        invoice.setLines(new HashSet<>());
        invoice.getLines().add(line);
        invoice.setPayments(new HashSet<>());
    }

    private String extractText(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        StringBuilder sb = new StringBuilder();
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            sb.append(extractor.getTextFromPage(i)).append('\n');
        }
        reader.close();
        return sb.toString();
    }

    @Test
    void generate_issuedInvoice_returnsNonEmptyPdf() {
        byte[] pdf = service.generate(invoice);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void generate_issuedInvoice_includesNumberClientAndTotals() throws Exception {
        String text = extractText(service.generate(invoice));

        assertThat(text).contains("Facture");
        assertThat(text).contains("FACT-2026-001");
        assertThat(text).contains("Globex Industries");
        assertThat(text).contains("Acme Consulting");
        assertThat(text).contains("Consultance backend");
        assertThat(text).contains("BE0123456789");
        assertThat(text).contains("BE0987654321");
        assertThat(text).contains("1200,00 €");
        assertThat(text).contains("1452,00 €");
    }

    @Test
    void generate_draftInvoice_includesDraftBanner() throws Exception {
        invoice.setNumber(null);
        invoice.setStatus(InvoiceStatus.DRAFT);

        String text = extractText(service.generate(invoice));

        assertThat(text).contains("BROUILLON");
        assertThat(text).doesNotContain("FACT-2026-");
    }

    @Test
    void generate_missingCompanyInfo_doesNotCrash() {
        user.setCompanyName(null);
        user.setVatNumber(null);
        user.setBillingAddress(null);
        user.setFirstName("Jean");
        user.setLastName("Dupont");

        byte[] pdf = service.generate(invoice);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void generate_withPartialPayment_showsBalanceDue() throws Exception {
        com.example.invoiceflow.invoice.Payment payment = new com.example.invoiceflow.invoice.Payment();
        payment.setAmount(new BigDecimal("452.00"));
        invoice.getPayments().add(payment);

        String text = extractText(service.generate(invoice));

        assertThat(text).contains("Déjà payé");
        assertThat(text).contains("Solde dû");
        assertThat(text).contains("452,00 €");
        assertThat(text).contains("1000,00 €");
    }
}
