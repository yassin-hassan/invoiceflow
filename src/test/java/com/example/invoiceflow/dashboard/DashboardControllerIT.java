package com.example.invoiceflow.dashboard;

import com.example.invoiceflow.PostgresTestContainer;
import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.InvoiceStatus;
import com.example.invoiceflow.invoice.Payment;
import com.example.invoiceflow.quote.Quote;
import com.example.invoiceflow.quote.QuoteRepository;
import com.example.invoiceflow.quote.QuoteStatus;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class DashboardControllerIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private QuoteRepository quoteRepository;
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
        quoteRepository.deleteAll();
        clientRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setEmail("john.doe@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmailVerified(true);
        userRepository.save(user);

        client = new Client();
        client.setUser(user);
        client.setName("Acme Corp");
        client.setEmail("acme@example.com");
        clientRepository.save(client);

        token = jwtService.generateToken("john.doe@example.com", com.example.invoiceflow.user.Role.USER);
    }

    private Invoice seedInvoice(String number, InvoiceStatus status, LocalDate due,
                                BigDecimal lineHt, BigDecimal vatRate, BigDecimal... payments) {
        Invoice inv = new Invoice();
        inv.setUser(user);
        inv.setClient(client);
        inv.setNumber(number);
        inv.setStatus(status);
        inv.setIssueDate(LocalDate.now());
        inv.setDueDate(due);
        inv.setLines(new HashSet<>());
        inv.setPayments(new HashSet<>());

        InvoiceLine line = new InvoiceLine();
        line.setInvoice(inv);
        line.setDescription("work");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(lineHt);
        line.setVatRate(vatRate);
        line.setSortOrder(0);
        inv.getLines().add(line);

        int i = 0;
        for (BigDecimal amount : payments) {
            Payment p = new Payment();
            p.setInvoice(inv);
            p.setAmount(amount);
            p.setMethod("Bank transfer");
            p.setPaidAt(LocalDate.now().minusDays(i++));
            inv.getPayments().add(p);
        }
        return invoiceRepository.save(inv);
    }

    private Quote seedQuote(String number, QuoteStatus status) {
        Quote q = new Quote();
        q.setUser(user);
        q.setClient(client);
        q.setNumber(number);
        q.setStatus(status);
        q.setIssueDate(LocalDate.now());
        q.setExpiryDate(LocalDate.now().plusDays(30));
        return quoteRepository.save(q);
    }

    @Test
    void getDashboard_withoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDashboard_emptyAccount_returnsZeros() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.revenue").value(0.00))
                .andExpect(jsonPath("$.totals.outstanding").value(0.00))
                .andExpect(jsonPath("$.totals.overdue").value(0.00))
                .andExpect(jsonPath("$.counts.clients").value(1))
                .andExpect(jsonPath("$.counts.draftInvoices").value(0))
                .andExpect(jsonPath("$.counts.openQuotes").value(0))
                .andExpect(jsonPath("$.recentInvoices.length()").value(0))
                .andExpect(jsonPath("$.recentPayments.length()").value(0))
                .andExpect(jsonPath("$.topOverdue.length()").value(0));
    }

    @Test
    void getDashboard_withSeededData_aggregatesCorrectly() throws Exception {
        // PAID 1200, fully paid
        seedInvoice("FACT-2026-001", InvoiceStatus.PAID, LocalDate.now(),
                new BigDecimal("1000"), new BigDecimal("20"), new BigDecimal("1200"));
        // PARTIALLY_PAID 1200, 500 paid
        seedInvoice("FACT-2026-002", InvoiceStatus.PARTIALLY_PAID, LocalDate.now().plusDays(15),
                new BigDecimal("1000"), new BigDecimal("20"), new BigDecimal("500"));
        // OVERDUE 1200, 0 paid
        seedInvoice("FACT-2026-003", InvoiceStatus.OVERDUE, LocalDate.now().minusDays(10),
                new BigDecimal("1000"), new BigDecimal("20"));
        // DRAFT — should be excluded from totals
        seedInvoice("FACT-2026-004", InvoiceStatus.DRAFT, LocalDate.now(),
                new BigDecimal("500"), new BigDecimal("20"));

        seedQuote("DEV-2026-001", QuoteStatus.SENT);
        seedQuote("DEV-2026-002", QuoteStatus.ACCEPTED);

        mockMvc.perform(get("/api/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // revenue = 1200 + 500
                .andExpect(jsonPath("$.totals.revenue").value(1700.00))
                // outstanding = (1200-500) + 1200
                .andExpect(jsonPath("$.totals.outstanding").value(1900.00))
                // overdue = 1200
                .andExpect(jsonPath("$.totals.overdue").value(1200.00))
                .andExpect(jsonPath("$.counts.draftInvoices").value(1))
                .andExpect(jsonPath("$.counts.sentInvoices").value(1))
                .andExpect(jsonPath("$.counts.overdueInvoices").value(1))
                .andExpect(jsonPath("$.counts.paidInvoices").value(1))
                .andExpect(jsonPath("$.counts.openQuotes").value(1))
                .andExpect(jsonPath("$.recentInvoices.length()").value(4))
                .andExpect(jsonPath("$.recentPayments.length()").value(2))
                .andExpect(jsonPath("$.topOverdue.length()").value(1))
                .andExpect(jsonPath("$.topOverdue[0].number").value("FACT-2026-003"));
    }

    @Test
    void getDashboard_scopedToAuthenticatedUser() throws Exception {
        // Other user with their own data — should not bleed in.
        User other = new User();
        other.setEmail("other@example.com");
        other.setPasswordHash(passwordEncoder.encode("Password1"));
        other.setFirstName("Other");
        other.setLastName("User");
        other.setEmailVerified(true);
        userRepository.save(other);

        Client otherClient = new Client();
        otherClient.setUser(other);
        otherClient.setName("Other Co");
        otherClient.setEmail("other-client@example.com");
        clientRepository.save(otherClient);

        Invoice otherInv = new Invoice();
        otherInv.setUser(other);
        otherInv.setClient(otherClient);
        otherInv.setNumber("FACT-OTHER-001");
        otherInv.setStatus(InvoiceStatus.PAID);
        otherInv.setIssueDate(LocalDate.now());
        otherInv.setDueDate(LocalDate.now());
        otherInv.setLines(new HashSet<>());
        otherInv.setPayments(new HashSet<>());
        InvoiceLine ol = new InvoiceLine();
        ol.setInvoice(otherInv);
        ol.setDescription("work");
        ol.setQuantity(BigDecimal.ONE);
        ol.setUnitPrice(new BigDecimal("9999"));
        ol.setVatRate(BigDecimal.ZERO);
        ol.setSortOrder(0);
        otherInv.getLines().add(ol);
        Payment op = new Payment();
        op.setInvoice(otherInv);
        op.setAmount(new BigDecimal("9999"));
        op.setMethod("Cash");
        op.setPaidAt(LocalDate.now());
        otherInv.getPayments().add(op);
        invoiceRepository.save(otherInv);

        mockMvc.perform(get("/api/dashboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.revenue").value(0.00))
                .andExpect(jsonPath("$.counts.clients").value(1))
                .andExpect(jsonPath("$.recentInvoices.length()").value(0));
    }
}
