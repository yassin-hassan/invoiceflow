package com.example.invoiceflow.pdf;

import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.creditnote.CreditNote;
import com.example.invoiceflow.creditnote.CreditNoteLine;
import com.example.invoiceflow.creditnote.CreditNoteService;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.user.Address;
import com.example.invoiceflow.user.User;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditNotePdfService {

    private final CreditNoteService creditNoteService;

    private static final Locale FR_BE = Locale.of("fr", "BE");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color BRAND = new Color(63, 81, 181);
    private static final Color MUTED = new Color(100, 100, 100);
    private static final Color DRAFT_RED = new Color(183, 28, 28);

    private static final Font H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, BRAND);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 9, MUTED);
    private static final Font DRAFT_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, DRAFT_RED);

    public record RenderedPdf(String filename, byte[] bytes) {}

    @Transactional(readOnly = true)
    public RenderedPdf generateForUser(String email, UUID creditNoteId) {
        CreditNote creditNote = creditNoteService.get(email, creditNoteId);
        forceInitialize(creditNote);
        String filename = (creditNote.getNumber() != null
                ? creditNote.getNumber()
                : "brouillon-av-" + creditNote.getId()) + ".pdf";
        return new RenderedPdf(filename, generate(creditNote));
    }

    private void forceInitialize(CreditNote creditNote) {
        User user = creditNote.getUser();
        user.getEmail();
        if (user.getBillingAddress() != null) user.getBillingAddress().getStreet();
        Invoice invoice = creditNote.getOriginalInvoice();
        invoice.getNumber();
        Client client = invoice.getClient();
        client.getName();
        if (client.getBillingAddress() != null) client.getBillingAddress().getStreet();
        creditNote.getLines().forEach(line -> line.getInvoiceLine().getDescription());
    }

    public byte[] generate(CreditNote creditNote) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 50);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            if (creditNote.getNumber() == null) {
                addDraftBanner(doc);
            }

            addHeader(doc, creditNote);
            doc.add(spacer(12));
            addPartiesBlock(doc, creditNote);
            doc.add(spacer(12));
            addReasonBlock(doc, creditNote);
            doc.add(spacer(12));
            addLinesTable(doc, creditNote);
            doc.add(spacer(8));
            addTotalsBlock(doc, creditNote);
            addFooter(doc, creditNote);

            doc.close();
        } catch (DocumentException ex) {
            throw new IllegalStateException("Failed to render credit note PDF", ex);
        }
        return out.toByteArray();
    }

    private void addDraftBanner(Document doc) throws DocumentException {
        PdfPTable banner = new PdfPTable(1);
        banner.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase("BROUILLON — Sans valeur légale", DRAFT_FONT));
        cell.setBackgroundColor(new Color(255, 235, 238));
        cell.setBorderColor(DRAFT_RED);
        cell.setBorderWidth(1f);
        cell.setPadding(8f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        banner.addCell(cell);
        doc.add(banner);
        doc.add(spacer(8));
    }

    private void addHeader(Document doc, CreditNote creditNote) throws DocumentException {
        User user = creditNote.getUser();
        Invoice invoice = creditNote.getOriginalInvoice();
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{60, 40});

        PdfPCell sellerCell = new PdfPCell();
        sellerCell.setBorder(Rectangle.NO_BORDER);
        sellerCell.addElement(new Paragraph(sellerName(user), H2));
        if (user.getBillingAddress() != null) {
            sellerCell.addElement(new Paragraph(formatAddress(user.getBillingAddress()), SMALL));
        }
        if (user.getVatNumber() != null) {
            sellerCell.addElement(new Paragraph("TVA : " + user.getVatNumber(), SMALL));
        }
        if (user.getEmail() != null) {
            sellerCell.addElement(new Paragraph(user.getEmail(), SMALL));
        }
        header.addCell(sellerCell);

        PdfPCell metaCell = new PdfPCell();
        metaCell.setBorder(Rectangle.NO_BORDER);
        metaCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph title = new Paragraph("Note de crédit", H1);
        title.setAlignment(Element.ALIGN_RIGHT);
        metaCell.addElement(title);
        if (creditNote.getNumber() != null) {
            Paragraph number = new Paragraph(creditNote.getNumber(), BODY_BOLD);
            number.setAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(number);
        }
        Paragraph issue = new Paragraph("Émise le " + DATE_FMT.format(creditNote.getIssueDate()), SMALL);
        issue.setAlignment(Element.ALIGN_RIGHT);
        metaCell.addElement(issue);
        if (invoice.getNumber() != null) {
            Paragraph ref = new Paragraph("Réf. facture : " + invoice.getNumber(), SMALL);
            ref.setAlignment(Element.ALIGN_RIGHT);
            metaCell.addElement(ref);
        }
        header.addCell(metaCell);

        doc.add(header);
    }

    private void addPartiesBlock(Document doc, CreditNote creditNote) throws DocumentException {
        Client client = creditNote.getOriginalInvoice().getClient();
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{50, 50});

        PdfPCell spacer = new PdfPCell();
        spacer.setBorder(Rectangle.NO_BORDER);
        table.addCell(spacer);

        PdfPCell billTo = new PdfPCell();
        billTo.setBorder(Rectangle.NO_BORDER);
        billTo.setPaddingLeft(8f);
        billTo.setBackgroundColor(new Color(245, 247, 250));
        billTo.addElement(new Paragraph("Crédit à", SMALL));
        billTo.addElement(new Paragraph(client.getName(), H2));
        if (client.getBillingAddress() != null) {
            billTo.addElement(new Paragraph(formatAddress(client.getBillingAddress()), BODY));
        }
        if (client.getVatNumber() != null) {
            billTo.addElement(new Paragraph("TVA : " + client.getVatNumber(), BODY));
        }
        if (client.getEmail() != null) {
            billTo.addElement(new Paragraph(client.getEmail(), SMALL));
        }
        table.addCell(billTo);

        doc.add(table);
    }

    private void addReasonBlock(Document doc, CreditNote creditNote) throws DocumentException {
        Paragraph reason = new Paragraph();
        reason.add(new Chunk("Motif : ", BODY_BOLD));
        reason.add(new Chunk(creditNote.getReason(), BODY));
        doc.add(reason);
    }

    private void addLinesTable(Document doc, CreditNote creditNote) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{50, 10, 15, 10, 15});

        addLinesHeaderCell(table, "Description", Element.ALIGN_LEFT);
        addLinesHeaderCell(table, "Qté", Element.ALIGN_RIGHT);
        addLinesHeaderCell(table, "P.U. HTVA", Element.ALIGN_RIGHT);
        addLinesHeaderCell(table, "TVA", Element.ALIGN_RIGHT);
        addLinesHeaderCell(table, "Total HTVA", Element.ALIGN_RIGHT);

        creditNote.getLines().stream()
                .sorted(Comparator.comparingInt(CreditNoteLine::getSortOrder))
                .forEach(line -> {
                    InvoiceLine source = line.getInvoiceLine();
                    addLineCell(table, source.getDescription(), Element.ALIGN_LEFT);
                    addLineCell(table, formatQuantity(line.getQuantity()), Element.ALIGN_RIGHT);
                    addLineCell(table, formatCurrency(source.getUnitPrice()), Element.ALIGN_RIGHT);
                    addLineCell(table, formatPercent(source.getVatRate()), Element.ALIGN_RIGHT);
                    addLineCell(table, formatCurrency(lineTotalExclVat(line).negate()), Element.ALIGN_RIGHT);
                });

        doc.add(table);
    }

    private void addLinesHeaderCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BRAND);
        cell.setPhrase(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
        cell.setHorizontalAlignment(align);
        cell.setPadding(6f);
        cell.setBorderColor(BRAND);
        table.addCell(cell);
    }

    private void addLineCell(PdfPTable table, String text, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BODY));
        cell.setHorizontalAlignment(align);
        cell.setPadding(5f);
        cell.setBorderColor(new Color(220, 220, 220));
        table.addCell(cell);
    }

    private void addTotalsBlock(Document doc, CreditNote creditNote) throws DocumentException {
        BigDecimal subtotal = creditNote.getLines().stream()
                .map(this::lineTotalExclVat)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVat = creditNote.getLines().stream()
                .map(line -> lineTotalExclVat(line)
                        .multiply(line.getInvoiceLine().getVatRate())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInclVat = subtotal.add(totalVat);

        PdfPTable wrapper = new PdfPTable(2);
        wrapper.setWidthPercentage(100);
        wrapper.setWidths(new float[]{55, 45});

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setPadding(0f);

        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(100);
        totals.setWidths(new float[]{60, 40});
        addTotalsRow(totals, "Sous-total HTVA", formatCurrency(subtotal.negate()), false);
        addTotalsRow(totals, "TVA", formatCurrency(totalVat.negate()), false);
        addTotalsRow(totals, "Total TVAC", formatCurrency(totalInclVat.negate()), true);
        right.addElement(totals);
        wrapper.addCell(right);

        doc.add(wrapper);
    }

    private void addTotalsRow(PdfPTable table, String label, String value, boolean emphasized) {
        Font labelFont = emphasized ? BODY_BOLD : BODY;
        Font valueFont = emphasized ? BODY_BOLD : BODY;
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setPadding(5f);
        labelCell.setBorder(emphasized ? Rectangle.TOP : Rectangle.NO_BORDER);
        labelCell.setBorderColor(MUTED);
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(5f);
        valueCell.setBorder(emphasized ? Rectangle.TOP : Rectangle.NO_BORDER);
        valueCell.setBorderColor(MUTED);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private void addFooter(Document doc, CreditNote creditNote) throws DocumentException {
        doc.add(spacer(20));
        String footer = "Document généré automatiquement par InvoiceFlow.";
        if (creditNote.getUser().getVatNumber() == null) {
            footer += " Régime particulier de franchise des petites entreprises.";
        }
        Paragraph p = new Paragraph(footer, SMALL);
        p.setAlignment(Element.ALIGN_CENTER);
        doc.add(p);
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(height);
        return p;
    }

    private String sellerName(User user) {
        if (user.getCompanyName() != null && !user.getCompanyName().isBlank()) {
            return user.getCompanyName();
        }
        String first = user.getFirstName() == null ? "" : user.getFirstName();
        String last = user.getLastName() == null ? "" : user.getLastName();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? "—" : full;
    }

    private String formatAddress(Address address) {
        StringBuilder sb = new StringBuilder();
        if (address.getStreet() != null) sb.append(address.getStreet());
        if (address.getPostalCode() != null || address.getCity() != null) {
            if (sb.length() > 0) sb.append("\n");
            if (address.getPostalCode() != null) sb.append(address.getPostalCode()).append(' ');
            if (address.getCity() != null) sb.append(address.getCity());
        }
        if (address.getCountry() != null) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(address.getCountry());
        }
        return sb.toString();
    }

    private BigDecimal lineTotalExclVat(CreditNoteLine line) {
        return line.getQuantity()
                .multiply(line.getInvoiceLine().getUnitPrice())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String formatCurrency(BigDecimal amount) {
        NumberFormat fmt = NumberFormat.getNumberInstance(FR_BE);
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(2);
        return fmt.format(amount) + " €";
    }

    private String formatQuantity(BigDecimal qty) {
        NumberFormat fmt = NumberFormat.getNumberInstance(FR_BE);
        fmt.setMinimumFractionDigits(0);
        fmt.setMaximumFractionDigits(2);
        return fmt.format(qty);
    }

    private String formatPercent(BigDecimal rate) {
        NumberFormat fmt = NumberFormat.getNumberInstance(FR_BE);
        fmt.setMinimumFractionDigits(0);
        fmt.setMaximumFractionDigits(2);
        return fmt.format(rate) + " %";
    }
}
