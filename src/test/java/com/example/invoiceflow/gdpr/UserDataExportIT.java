package com.example.invoiceflow.gdpr;

import com.example.invoiceflow.PostgresTestContainer;
import com.example.invoiceflow.audit.AuditAction;
import com.example.invoiceflow.audit.AuditLog;
import com.example.invoiceflow.audit.AuditLogRepository;
import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.InvoiceStatus;
import com.example.invoiceflow.product.Product;
import com.example.invoiceflow.product.ProductRepository;
import com.example.invoiceflow.quote.Quote;
import com.example.invoiceflow.quote.QuoteLine;
import com.example.invoiceflow.quote.QuoteRepository;
import com.example.invoiceflow.quote.QuoteStatus;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.user.Role;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class UserDataExportIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserDataExportService service;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private QuoteRepository quoteRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        auditLogRepository.deleteAll();
        invoiceRepository.deleteAll();
        quoteRepository.deleteAll();
        productRepository.deleteAll();
        clientRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setEmail("gdpr.user@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setFirstName("Greta");
        user.setLastName("Doe");
        user.setEmailVerified(true);
        user.setPreferredLanguage("FR");
        userRepository.save(user);

        Client client = new Client();
        client.setUser(user);
        client.setName("Acme Corp");
        client.setEmail("acme@example.com");
        clientRepository.save(client);

        Invoice invoice = new Invoice();
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setNumber("F-2026-001");
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setIssueDate(LocalDate.of(2026, 3, 1));
        invoice.setDueDate(LocalDate.of(2026, 3, 31));
        InvoiceLine il = new InvoiceLine();
        il.setInvoice(invoice);
        il.setDescription("Service");
        il.setQuantity(BigDecimal.ONE);
        il.setUnitPrice(new BigDecimal("100.00"));
        il.setVatRate(new BigDecimal("21.00"));
        il.setSortOrder(0);
        Set<InvoiceLine> ilines = new LinkedHashSet<>();
        ilines.add(il);
        invoice.setLines(ilines);
        invoiceRepository.save(invoice);

        Quote quote = new Quote();
        quote.setUser(user);
        quote.setClient(client);
        quote.setNumber("Q-2026-001");
        quote.setStatus(QuoteStatus.DRAFT);
        quote.setIssueDate(LocalDate.of(2026, 2, 15));
        quote.setExpiryDate(LocalDate.of(2026, 3, 15));
        QuoteLine ql = new QuoteLine();
        ql.setQuote(quote);
        ql.setDescription("Consulting");
        ql.setQuantity(BigDecimal.ONE);
        ql.setUnitPrice(new BigDecimal("200.00"));
        ql.setVatRate(new BigDecimal("21.00"));
        ql.setSortOrder(0);
        List<QuoteLine> qlines = new ArrayList<>();
        qlines.add(ql);
        quote.setLines(qlines);
        quoteRepository.save(quote);

        Product product = new Product();
        product.setUser(user);
        product.setName("Hourly rate");
        product.setReference("HR-1");
        product.setUnitPrice(new BigDecimal("90.00"));
        product.setVatRate(new BigDecimal("21.00"));
        product.setUnit("hour");
        product.setActive(true);
        productRepository.save(product);

        token = jwtService.generateToken("gdpr.user@example.com", Role.USER);
    }

    @Test
    void exportAllData_zipContainsExpectedEntries() throws Exception {
        byte[] zip = service.exportAllData("gdpr.user@example.com");

        Map<String, byte[]> entries = readZip(zip);

        assertThat(entries).containsKeys(
                "README.txt",
                "profile.json",
                "clients.json",
                "invoices.json",
                "quotes.json",
                "credit-notes.json",
                "products.json",
                "audit-log.json"
        );

        // PDF rendered for the seeded invoice
        assertThat(entries).containsKey("pdfs/invoices/F-2026-001.pdf");
        assertThat(entries.get("pdfs/invoices/F-2026-001.pdf").length).isGreaterThan(0);

        // Draft quote falls back to "draft-<id>" naming
        assertThat(entries.keySet().stream().filter(k -> k.startsWith("pdfs/quotes/")).toList())
                .hasSize(1);

        String profile = new String(entries.get("profile.json"));
        assertThat(profile).contains("gdpr.user@example.com");
        assertThat(profile).contains("\"firstName\" : \"Greta\"");

        String clientsJson = new String(entries.get("clients.json"));
        assertThat(clientsJson).contains("Acme Corp");

        String invoicesJson = new String(entries.get("invoices.json"));
        assertThat(invoicesJson).contains("F-2026-001");
        assertThat(invoicesJson).contains("\"status\" : \"SENT\"");
    }

    @Test
    void exportAllData_recordsAuditLog() {
        service.exportAllData("gdpr.user@example.com");

        List<AuditLog> logs = auditLogRepository.findByActorEmailOrderByOccurredAtDesc("gdpr.user@example.com");
        assertThat(logs)
                .extracting(AuditLog::getAction)
                .contains(AuditAction.USER_DATA_EXPORTED);
    }

    @Test
    void endpoint_returnsZipWithCorrectHeaders() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/users/me/data-export")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).isEqualTo("application/zip");

        String disposition = result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).startsWith("attachment; filename=\"invoiceflow-data-export-");
        assertThat(disposition).endsWith(".zip\"");

        byte[] body = result.getResponse().getContentAsByteArray();
        Map<String, byte[]> entries = readZip(body);
        assertThat(entries).containsKeys("README.txt", "profile.json", "invoices.json");
    }

    @Test
    void endpoint_withoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/users/me/data-export"))
                .andExpect(status().isForbidden());
    }

    private Map<String, byte[]> readZip(byte[] bytes) throws Exception {
        Map<String, byte[]> out = new HashMap<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                out.put(e.getName(), zin.readAllBytes());
                zin.closeEntry();
            }
        }
        return out;
    }
}
