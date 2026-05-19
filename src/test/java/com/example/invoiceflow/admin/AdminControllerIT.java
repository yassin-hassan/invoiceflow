package com.example.invoiceflow.admin;

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
import com.example.invoiceflow.invoice.Payment;
import com.example.invoiceflow.invoice.PaymentRepository;
import com.example.invoiceflow.quote.QuoteRepository;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.user.Role;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AdminControllerIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private QuoteRepository quoteRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    @MockitoBean private JavaMailSender mailSender;

    private MockMvc mockMvc;
    private String userToken;
    private String adminToken;
    private User user;
    private User admin;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        Session session = Session.getInstance(new Properties());
        org.mockito.Mockito.when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(session));

        paymentRepository.deleteAll();
        invoiceRepository.deleteAll();
        quoteRepository.deleteAll();
        clientRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setEmailVerified(true);
        user.setRole(Role.USER);
        userRepository.save(user);
        userToken = jwtService.generateToken("user@example.com", Role.USER);

        admin = new User();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("Password1"));
        admin.setEmailVerified(true);
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        adminToken = jwtService.generateToken("admin@example.com", Role.ADMIN);
    }

    // --- Phase 1 smoke (kept) ---

    @Test
    void ping_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ping_userRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void ping_adminRole_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    // --- GET /api/admin/users ---

    @Test
    void listUsers_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_asAdmin_returnsBothUsersWithStats() throws Exception {
        seedClientInvoicePayment(user, new BigDecimal("250.00"));

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.email == 'user@example.com')].clientCount").value(1))
                .andExpect(jsonPath("$[?(@.email == 'user@example.com')].invoiceCount").value(1))
                .andExpect(jsonPath("$[?(@.email == 'user@example.com')].totalRevenue").value(250.00))
                .andExpect(jsonPath("$[?(@.email == 'admin@example.com')].clientCount").value(0))
                .andExpect(jsonPath("$[?(@.email == 'admin@example.com')].totalRevenue").value(0));
    }

    // --- GET /api/admin/users/{id} ---

    @Test
    void getUser_asAdmin_returnsDetail() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + user.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getUser_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/admin/users/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // --- PATCH /api/admin/users/{id}/status ---

    @Test
    void updateStatus_disableUser_persistsAndAudits() throws Exception {
        mockMvc.perform(patch("/api/admin/users/" + user.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updated.isActive()).isFalse();

        AuditLog audit = auditLogRepository.findAll().stream()
                .filter(a -> a.getAction() == AuditAction.ADMIN_USER_STATUS_CHANGED)
                .findFirst().orElseThrow();
        assertThat(audit.getActorEmail()).isEqualTo("admin@example.com");
        assertThat(audit.getResourceId()).isEqualTo(user.getId().toString());
        assertThat(audit.getDetails()).containsEntry("from", true).containsEntry("to", false);
    }

    @Test
    void updateStatus_selfTarget_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/users/" + admin.getId() + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isBadRequest());

        User reread = userRepository.findById(admin.getId()).orElseThrow();
        assertThat(reread.isActive()).isTrue();
    }

    // --- PATCH /api/admin/users/{id}/role ---

    @Test
    void updateRole_promoteUserToAdmin_persistsAndAudits() throws Exception {
        mockMvc.perform(patch("/api/admin/users/" + user.getId() + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        AuditLog audit = auditLogRepository.findAll().stream()
                .filter(a -> a.getAction() == AuditAction.ADMIN_USER_ROLE_CHANGED)
                .findFirst().orElseThrow();
        assertThat(audit.getDetails()).containsEntry("from", "USER").containsEntry("to", "ADMIN");
    }

    @Test
    void updateRole_selfTarget_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/users/" + admin.getId() + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": \"USER\"}"))
                .andExpect(status().isBadRequest());
    }

    // --- POST /api/admin/users/{id}/password-reset ---

    @Test
    void triggerPasswordReset_sendsEmailAndAudits() throws Exception {
        mockMvc.perform(post("/api/admin/users/" + user.getId() + "/password-reset")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        AuditLog audit = auditLogRepository.findAll().stream()
                .filter(a -> a.getAction() == AuditAction.ADMIN_USER_PASSWORD_RESET_SENT)
                .findFirst().orElseThrow();
        assertThat(audit.getActorEmail()).isEqualTo("admin@example.com");
        assertThat(audit.getResourceId()).isEqualTo(user.getId().toString());

        org.mockito.Mockito.verify(mailSender).send(org.mockito.Mockito.any(MimeMessage.class));
    }

    // --- GET /api/admin/audit-logs ---

    @Test
    void auditLogs_listFilteredByAction_returnsMatching() throws Exception {
        seedAuditRow(AuditAction.LOGIN_SUCCESS, "user@example.com");
        seedAuditRow(AuditAction.LOGIN_FAILED, "user@example.com");
        seedAuditRow(AuditAction.INVOICE_SENT, "user@example.com");

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("action", "LOGIN_FAILED")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].action").value("LOGIN_FAILED"));
    }

    @Test
    void auditLogs_filterByActor_isCaseInsensitiveSubstring() throws Exception {
        seedAuditRow(AuditAction.LOGIN_SUCCESS, "alice@example.com");
        seedAuditRow(AuditAction.LOGIN_SUCCESS, "bob@example.com");

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("actor", "ALICE")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].actorEmail").value("alice@example.com"));
    }

    @Test
    void auditLogs_pagination_respectsSizeAndOrder() throws Exception {
        for (int i = 0; i < 3; i++) {
            seedAuditRow(AuditAction.LOGIN_SUCCESS, "user@example.com");
        }

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("size", "2")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void auditLogs_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/admin/audit-logs.csv ---

    @Test
    void auditLogsCsv_returnsHeaderAndRowsWithAttachmentDisposition() throws Exception {
        seedAuditRow(AuditAction.LOGIN_SUCCESS, "user@example.com");

        mockMvc.perform(get("/api/admin/audit-logs.csv")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"audit-logs-")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "occurred_at,actor_email,action,resource_type,resource_id,ip_address,user_agent,details")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("LOGIN_SUCCESS")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("user@example.com")));
    }

    // --- helpers ---

    private void seedClientInvoicePayment(User owner, BigDecimal paymentAmount) {
        Client client = new Client();
        client.setUser(owner);
        client.setName("Acme");
        client.setEmail("acme@example.com");
        clientRepository.save(client);

        Invoice invoice = new Invoice();
        invoice.setUser(owner);
        invoice.setClient(client);
        invoice.setNumber("FACT-2026-001");
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setLines(new HashSet<>());
        invoice.setPayments(new HashSet<>());

        InvoiceLine line = new InvoiceLine();
        line.setInvoice(invoice);
        line.setQuantity(new BigDecimal("1"));
        line.setUnitPrice(new BigDecimal("250.00"));
        line.setVatRate(new BigDecimal("0.00"));
        line.setSortOrder(0);
        line.setDescription("Work");
        invoice.getLines().add(line);

        invoiceRepository.save(invoice);

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(paymentAmount);
        payment.setMethod("bank");
        payment.setPaidAt(LocalDate.now());
        paymentRepository.save(payment);
    }

    private void seedAuditRow(AuditAction action, String actorEmail) {
        AuditLog log = new AuditLog();
        log.setOccurredAt(LocalDateTime.now());
        log.setAction(action);
        log.setActorEmail(actorEmail);
        auditLogRepository.save(log);
    }
}
