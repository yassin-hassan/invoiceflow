package com.example.invoiceflow.exports;

import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.config.I18nConfig;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.InvoiceStatus;
import com.example.invoiceflow.invoice.Payment;
import com.example.invoiceflow.user.Address;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AccountingExportService {

    private final UserService userService;
    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final MessageSource messageSource;

    @Transactional(readOnly = true)
    public byte[] exportAccounting(String email, LocalDate from, LocalDate to, InvoiceStatus status) {
        User user = userService.getByEmail(email);
        Locale locale = I18nConfig.toLocale(user.getPreferredLanguage());

        List<Invoice> invoices = invoiceRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .filter(i -> from == null || !i.getIssueDate().isBefore(from))
                .filter(i -> to == null || !i.getIssueDate().isAfter(to))
                .filter(i -> status == null || i.getStatus() == status)
                .toList();
        List<Client> clients = clientRepository.findByUserAndIsActiveTrue(user);

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(wb);
            writeInvoicesSheet(wb, styles, invoices, locale);
            writePaymentsSheet(wb, styles, invoices, from, to, locale);
            writeClientsSheet(wb, styles, clients, locale);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to render accounting workbook", ex);
        }
    }

    private void writeInvoicesSheet(XSSFWorkbook wb, Styles styles, List<Invoice> invoices, Locale locale) {
        Sheet sheet = wb.createSheet(t("export.sheet.invoices", locale));
        String[] headers = {
                t("export.col.invoice.number", locale),
                t("export.col.invoice.issueDate", locale),
                t("export.col.invoice.dueDate", locale),
                t("export.col.invoice.status", locale),
                t("export.col.invoice.sentAt", locale),
                t("export.col.invoice.clientName", locale),
                t("export.col.invoice.clientVat", locale),
                t("export.col.invoice.subtotalHt", locale),
                t("export.col.invoice.vat", locale),
                t("export.col.invoice.totalTtc", locale),
                t("export.col.invoice.paid", locale),
                t("export.col.invoice.balanceDue", locale)
        };
        writeHeaderRow(sheet, headers, styles);

        int rowIdx = 1;
        for (Invoice inv : invoices) {
            BigDecimal subtotal = invoiceSubtotalExclVat(inv);
            BigDecimal vat = invoiceTotalVat(inv);
            BigDecimal total = subtotal.add(vat);
            BigDecimal paid = inv.getPayments().stream()
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal balance = total.subtract(paid);

            Row row = sheet.createRow(rowIdx++);
            setString(row, 0, inv.getNumber());
            setDate(row, 1, inv.getIssueDate(), styles);
            setDate(row, 2, inv.getDueDate(), styles);
            setString(row, 3, inv.getStatus() == null ? "" : inv.getStatus().name());
            setDate(row, 4, inv.getSentAt() == null ? null : inv.getSentAt().toLocalDate(), styles);
            setString(row, 5, inv.getClient() == null ? "" : inv.getClient().getName());
            setString(row, 6, inv.getClient() == null ? "" : inv.getClient().getVatNumber());
            setMoney(row, 7, subtotal, styles);
            setMoney(row, 8, vat, styles);
            setMoney(row, 9, total, styles);
            setMoney(row, 10, paid, styles);
            setMoney(row, 11, balance, styles);
        }

        finalizeSheet(sheet, headers.length);
    }

    private void writePaymentsSheet(XSSFWorkbook wb, Styles styles, List<Invoice> invoices,
                                    LocalDate from, LocalDate to, Locale locale) {
        Sheet sheet = wb.createSheet(t("export.sheet.payments", locale));
        String[] headers = {
                t("export.col.payment.invoiceNumber", locale),
                t("export.col.payment.paidAt", locale),
                t("export.col.payment.amount", locale),
                t("export.col.payment.method", locale),
                t("export.col.payment.notes", locale)
        };
        writeHeaderRow(sheet, headers, styles);

        List<PaymentRow> rows = invoices.stream()
                .flatMap(inv -> inv.getPayments().stream().map(p -> new PaymentRow(inv, p)))
                .filter(pr -> from == null || !pr.payment.getPaidAt().isBefore(from))
                .filter(pr -> to == null || !pr.payment.getPaidAt().isAfter(to))
                .sorted(Comparator.comparing((PaymentRow pr) -> pr.payment.getPaidAt()).reversed())
                .toList();

        int rowIdx = 1;
        for (PaymentRow pr : rows) {
            Row row = sheet.createRow(rowIdx++);
            setString(row, 0, pr.invoice.getNumber());
            setDate(row, 1, pr.payment.getPaidAt(), styles);
            setMoney(row, 2, pr.payment.getAmount(), styles);
            setString(row, 3, pr.payment.getMethod());
            setString(row, 4, pr.payment.getNotes());
        }

        finalizeSheet(sheet, headers.length);
    }

    private void writeClientsSheet(XSSFWorkbook wb, Styles styles, List<Client> clients, Locale locale) {
        Sheet sheet = wb.createSheet(t("export.sheet.clients", locale));
        String[] headers = {
                t("export.col.client.name", locale),
                t("export.col.client.email", locale),
                t("export.col.client.phone", locale),
                t("export.col.client.vatNumber", locale),
                t("export.col.client.city", locale),
                t("export.col.client.country", locale),
                t("export.col.client.active", locale)
        };
        writeHeaderRow(sheet, headers, styles);

        int rowIdx = 1;
        for (Client c : clients) {
            Address addr = c.getBillingAddress();
            Row row = sheet.createRow(rowIdx++);
            setString(row, 0, c.getName());
            setString(row, 1, c.getEmail());
            setString(row, 2, c.getPhone());
            setString(row, 3, c.getVatNumber());
            setString(row, 4, addr == null ? "" : addr.getCity());
            setString(row, 5, addr == null ? "" : addr.getCountry());
            setString(row, 6, c.isActive() ? "✓" : "");
        }

        finalizeSheet(sheet, headers.length);
    }

    private void writeHeaderRow(Sheet sheet, String[] headers, Styles styles) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(styles.header);
        }
        sheet.createFreezePane(0, 1);
    }

    private void finalizeSheet(Sheet sheet, int colCount) {
        for (int i = 0; i < colCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void setString(Row row, int col, String value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
    }

    private void setDate(Row row, int col, LocalDate value, Styles styles) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value);
            cell.setCellStyle(styles.date);
        }
    }

    private void setMoney(Row row, int col, BigDecimal value, Styles styles) {
        Cell cell = row.createCell(col);
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        cell.setCellValue(v.doubleValue());
        cell.setCellStyle(styles.money);
    }

    private BigDecimal invoiceSubtotalExclVat(Invoice invoice) {
        return invoice.getLines().stream()
                .map(this::lineExclVat)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal invoiceTotalVat(Invoice invoice) {
        return invoice.getLines().stream()
                .map(l -> lineExclVat(l).multiply(l.getVatRate())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal lineExclVat(InvoiceLine line) {
        return line.getQuantity().multiply(line.getUnitPrice());
    }

    private String t(String code, Locale locale) {
        return messageSource.getMessage(code, null, code, locale);
    }

    private record PaymentRow(Invoice invoice, Payment payment) {}

    private static final class Styles {
        final CellStyle header;
        final CellStyle date;
        final CellStyle money;

        Styles(XSSFWorkbook wb) {
            DataFormat fmt = wb.createDataFormat();

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header = wb.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);

            date = wb.createCellStyle();
            date.setDataFormat(fmt.getFormat("dd/MM/yyyy"));

            money = wb.createCellStyle();
            money.setDataFormat(fmt.getFormat("#,##0.00\\ \"€\""));
        }
    }
}
