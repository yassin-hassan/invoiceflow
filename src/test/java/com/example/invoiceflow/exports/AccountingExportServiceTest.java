package com.example.invoiceflow.exports;

import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.InvoiceStatus;
import com.example.invoiceflow.invoice.Payment;
import com.example.invoiceflow.user.Address;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountingExportServiceTest {

    @Mock private UserService userService;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private MessageSource messageSource;

    @InjectMocks
    private AccountingExportService service;

    private User user;
    private Client client;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        user.setPreferredLanguage("FR");

        client = new Client();
        client.setId(UUID.randomUUID());
        client.setUser(user);
        client.setName("Acme Corp");
        client.setEmail("acme@example.com");
        client.setVatNumber("BE0123456789");
        Address addr = new Address();
        addr.setCity("Brussels");
        addr.setCountry("BE");
        client.setBillingAddress(addr);
        client.setActive(true);

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(messageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void exportAccounting_emptyData_writesThreeSheetsWithHeadersOnly() throws Exception {
        when(invoiceRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of());
        when(clientRepository.findByUserAndIsActiveTrue(user)).thenReturn(List.of());

        byte[] bytes = service.exportAccounting("user@example.com", null, null, null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(3);
            assertThat(sheetByKey(wb, "export.sheet.invoices").getPhysicalNumberOfRows()).isEqualTo(1);
            assertThat(sheetByKey(wb, "export.sheet.payments").getPhysicalNumberOfRows()).isEqualTo(1);
            assertThat(sheetByKey(wb, "export.sheet.clients").getPhysicalNumberOfRows()).isEqualTo(1);
        }
    }

    @Test
    void exportAccounting_twoInvoicesAndOnePayment_writesDataRows() throws Exception {
        Invoice paid = buildInvoice("F-2026-001", LocalDate.of(2026, 3, 1), InvoiceStatus.PAID,
                new BigDecimal("100.00"), new BigDecimal("21.00"));
        Payment p = new Payment();
        p.setAmount(new BigDecimal("121.00"));
        p.setMethod("Bank transfer");
        p.setPaidAt(LocalDate.of(2026, 3, 10));
        p.setInvoice(paid);
        paid.getPayments().add(p);

        Invoice sent = buildInvoice("F-2026-002", LocalDate.of(2026, 4, 1), InvoiceStatus.SENT,
                new BigDecimal("200.00"), new BigDecimal("21.00"));

        when(invoiceRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(paid, sent));
        when(clientRepository.findByUserAndIsActiveTrue(user)).thenReturn(List.of(client));

        byte[] bytes = service.exportAccounting("user@example.com", null, null, null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet invoices = sheetByKey(wb, "export.sheet.invoices");
            assertThat(invoices.getPhysicalNumberOfRows()).isEqualTo(3);

            Sheet payments = sheetByKey(wb, "export.sheet.payments");
            assertThat(payments.getPhysicalNumberOfRows()).isEqualTo(2);
            Row paymentRow = payments.getRow(1);
            Cell amountCell = paymentRow.getCell(2);
            assertThat(amountCell.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(amountCell.getNumericCellValue()).isEqualTo(121.00);

            Sheet clients = sheetByKey(wb, "export.sheet.clients");
            assertThat(clients.getPhysicalNumberOfRows()).isEqualTo(2);
            assertThat(clients.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Acme Corp");
        }
    }

    @Test
    void exportAccounting_fromToFilter_excludesInvoicesOutsideWindow() throws Exception {
        Invoice early = buildInvoice("F-001", LocalDate.of(2026, 1, 15), InvoiceStatus.PAID,
                new BigDecimal("100.00"), new BigDecimal("21.00"));
        Invoice inWindow = buildInvoice("F-002", LocalDate.of(2026, 3, 5), InvoiceStatus.PAID,
                new BigDecimal("200.00"), new BigDecimal("21.00"));
        Invoice late = buildInvoice("F-003", LocalDate.of(2026, 5, 20), InvoiceStatus.PAID,
                new BigDecimal("300.00"), new BigDecimal("21.00"));

        when(invoiceRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(early, inWindow, late));
        when(clientRepository.findByUserAndIsActiveTrue(user)).thenReturn(List.of());

        byte[] bytes = service.exportAccounting("user@example.com",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30), null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet invoices = sheetByKey(wb, "export.sheet.invoices");
            assertThat(invoices.getPhysicalNumberOfRows()).isEqualTo(2);
            assertThat(invoices.getRow(1).getCell(0).getStringCellValue()).isEqualTo("F-002");
        }
    }

    @Test
    void exportAccounting_statusFilter_excludesNonMatching() throws Exception {
        Invoice paid = buildInvoice("F-001", LocalDate.of(2026, 3, 1), InvoiceStatus.PAID,
                new BigDecimal("100.00"), new BigDecimal("21.00"));
        Invoice sent = buildInvoice("F-002", LocalDate.of(2026, 3, 2), InvoiceStatus.SENT,
                new BigDecimal("100.00"), new BigDecimal("21.00"));

        when(invoiceRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(paid, sent));
        when(clientRepository.findByUserAndIsActiveTrue(user)).thenReturn(List.of());

        byte[] bytes = service.exportAccounting("user@example.com", null, null, InvoiceStatus.PAID);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet invoices = sheetByKey(wb, "export.sheet.invoices");
            assertThat(invoices.getPhysicalNumberOfRows()).isEqualTo(2);
            assertThat(invoices.getRow(1).getCell(3).getStringCellValue()).isEqualTo("PAID");
        }
    }

    @Test
    void exportAccounting_cellTypes_areNumericAndDate() throws Exception {
        Invoice inv = buildInvoice("F-001", LocalDate.of(2026, 3, 1), InvoiceStatus.PAID,
                new BigDecimal("100.00"), new BigDecimal("21.00"));

        when(invoiceRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(inv));
        when(clientRepository.findByUserAndIsActiveTrue(user)).thenReturn(List.of());

        byte[] bytes = service.exportAccounting("user@example.com", null, null, null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row row = sheetByKey(wb, "export.sheet.invoices").getRow(1);

            Cell issueDate = row.getCell(1);
            assertThat(issueDate.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(issueDate)).isTrue();

            Cell subtotal = row.getCell(7);
            assertThat(subtotal.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(subtotal.getNumericCellValue()).isEqualTo(100.00);

            Cell vat = row.getCell(8);
            assertThat(vat.getNumericCellValue()).isEqualTo(21.00);

            Cell total = row.getCell(9);
            assertThat(total.getNumericCellValue()).isEqualTo(121.00);
        }
    }

    private Invoice buildInvoice(String number, LocalDate issueDate, InvoiceStatus status,
                                 BigDecimal lineTotalExclVat, BigDecimal vatRate) {
        Invoice inv = new Invoice();
        inv.setId(UUID.randomUUID());
        inv.setUser(user);
        inv.setClient(client);
        inv.setNumber(number);
        inv.setStatus(status);
        inv.setIssueDate(issueDate);
        inv.setDueDate(issueDate.plusDays(30));

        InvoiceLine line = new InvoiceLine();
        line.setDescription("Service");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(lineTotalExclVat);
        line.setVatRate(vatRate);
        line.setInvoice(inv);
        Set<InvoiceLine> lines = new LinkedHashSet<>();
        lines.add(line);
        inv.setLines(lines);
        return inv;
    }

    private Sheet sheetByKey(XSSFWorkbook wb, String key) {
        Sheet s = wb.getSheet(key);
        assertThat(s).as("sheet for key %s", key).isNotNull();
        return s;
    }
}
