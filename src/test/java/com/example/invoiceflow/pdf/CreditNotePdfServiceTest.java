package com.example.invoiceflow.pdf;

import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.creditnote.CreditNote;
import com.example.invoiceflow.creditnote.CreditNoteLine;
import com.example.invoiceflow.creditnote.CreditNoteStatus;
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

class CreditNotePdfServiceTest {

    private final CreditNotePdfService service = new CreditNotePdfService(null);

    private CreditNote creditNote;
    private InvoiceLine invoiceLine;

    @BeforeEach
    void setUp() {
        Address userAddress = new Address();
        userAddress.setStreet("Rue de la Loi 16");
        userAddress.setPostalCode("1000");
        userAddress.setCity("Bruxelles");
        userAddress.setCountry("Belgique");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("freelance@example.com");
        user.setCompanyName("Acme Consulting");
        user.setVatNumber("BE0123456789");
        user.setBillingAddress(userAddress);

        Address clientAddress = new Address();
        clientAddress.setStreet("Avenue Louise 100");
        clientAddress.setPostalCode("1050");
        clientAddress.setCity("Ixelles");

        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setName("Globex Industries");
        client.setEmail("billing@globex.example");
        client.setVatNumber("BE0987654321");
        client.setBillingAddress(clientAddress);

        invoiceLine = new InvoiceLine();
        invoiceLine.setId(UUID.randomUUID());
        invoiceLine.setDescription("Consultance backend");
        invoiceLine.setQuantity(new BigDecimal("10"));
        invoiceLine.setUnitPrice(new BigDecimal("120.00"));
        invoiceLine.setVatRate(new BigDecimal("21.00"));
        invoiceLine.setSortOrder(0);

        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setNumber("FACT-2026-001");
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setIssueDate(LocalDate.of(2026, 4, 1));
        invoice.setDueDate(LocalDate.of(2026, 5, 1));
        invoice.setLines(new HashSet<>());
        invoice.getLines().add(invoiceLine);
        invoiceLine.setInvoice(invoice);

        CreditNoteLine line = new CreditNoteLine();
        line.setId(UUID.randomUUID());
        line.setInvoiceLine(invoiceLine);
        line.setQuantity(new BigDecimal("2"));
        line.setSortOrder(0);

        creditNote = new CreditNote();
        creditNote.setId(UUID.randomUUID());
        creditNote.setUser(user);
        creditNote.setOriginalInvoice(invoice);
        creditNote.setNumber("AV-2026-001");
        creditNote.setStatus(CreditNoteStatus.ISSUED);
        creditNote.setIssueDate(LocalDate.of(2026, 5, 13));
        creditNote.setReason("Erreur sur la quantité facturée");
        creditNote.getLines().add(line);
        line.setCreditNote(creditNote);
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
    void generate_issuedCreditNote_returnsNonEmptyPdf() {
        byte[] pdf = service.generate(creditNote);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void generate_issuedCreditNote_includesNumberInvoiceRefReasonAndNegativeTotals() throws Exception {
        String text = extractText(service.generate(creditNote));

        assertThat(text).contains("Note de crédit");
        assertThat(text).contains("AV-2026-001");
        assertThat(text).contains("FACT-2026-001");
        assertThat(text).contains("Globex Industries");
        assertThat(text).contains("Acme Consulting");
        assertThat(text).contains("Consultance backend");
        assertThat(text).contains("Erreur sur la quantité facturée");
        assertThat(text).contains("-240,00 €");
        assertThat(text).contains("-290,40 €");
    }

    @Test
    void generate_draftCreditNote_includesDraftBanner() throws Exception {
        creditNote.setNumber(null);
        creditNote.setStatus(CreditNoteStatus.DRAFT);

        String text = extractText(service.generate(creditNote));

        assertThat(text).contains("BROUILLON");
        assertThat(text).doesNotContain("AV-2026-");
    }
}
