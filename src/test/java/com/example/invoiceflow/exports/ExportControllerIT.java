package com.example.invoiceflow.exports;

import com.example.invoiceflow.PostgresTestContainer;
import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.InvoiceStatus;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.user.Role;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ExportControllerIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private String token;
    private User user;
    private Client client;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        invoiceRepository.deleteAll();
        clientRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setEmail("export@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setFirstName("Anna");
        user.setLastName("Exporter");
        user.setEmailVerified(true);
        user.setPreferredLanguage("FR");
        userRepository.save(user);

        client = new Client();
        client.setUser(user);
        client.setName("Acme Corp");
        client.setEmail("acme@example.com");
        clientRepository.save(client);

        token = jwtService.generateToken("export@example.com", Role.USER);
    }

    @Test
    void exportAccounting_returnsXlsxWithThreeSheets() throws Exception {
        seedInvoice("F-2026-001", LocalDate.of(2026, 3, 1), InvoiceStatus.PAID);

        MvcResult result = mockMvc.perform(get("/api/exports/accounting.xlsx")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String contentType = result.getResponse().getContentType();
        assertThat(contentType)
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        String disposition = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).startsWith("attachment; filename=\"accounting-export-");
        assertThat(disposition).endsWith(".xlsx\"");

        byte[] body = result.getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(3);
            Sheet invoices = wb.getSheetAt(0);
            assertThat(invoices.getPhysicalNumberOfRows()).isEqualTo(2);
        }
    }

    @Test
    void exportAccounting_withoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/exports/accounting.xlsx"))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportAccounting_statusFilter_returnsFilteredRows() throws Exception {
        seedInvoice("F-2026-001", LocalDate.of(2026, 3, 1), InvoiceStatus.PAID);
        seedInvoice("F-2026-002", LocalDate.of(2026, 3, 2), InvoiceStatus.SENT);

        MvcResult result = mockMvc.perform(get("/api/exports/accounting.xlsx")
                .param("status", "PAID")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet invoices = wb.getSheetAt(0);
            assertThat(invoices.getPhysicalNumberOfRows()).isEqualTo(2);
            assertThat(invoices.getRow(1).getCell(3).getStringCellValue()).isEqualTo("PAID");
        }
    }

    private Invoice seedInvoice(String number, LocalDate issueDate, InvoiceStatus status) {
        Invoice inv = new Invoice();
        inv.setUser(user);
        inv.setClient(client);
        inv.setNumber(number);
        inv.setStatus(status);
        inv.setIssueDate(issueDate);
        inv.setDueDate(issueDate.plusDays(30));

        InvoiceLine line = new InvoiceLine();
        line.setInvoice(inv);
        line.setDescription("Service");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setVatRate(new BigDecimal("21.00"));
        line.setSortOrder(0);
        Set<InvoiceLine> lines = new LinkedHashSet<>();
        lines.add(line);
        inv.setLines(lines);

        return invoiceRepository.save(inv);
    }
}
