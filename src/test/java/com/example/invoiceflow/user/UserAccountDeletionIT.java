package com.example.invoiceflow.user;

import com.example.invoiceflow.PostgresTestContainer;
import com.example.invoiceflow.audit.AuditAction;
import com.example.invoiceflow.audit.AuditLog;
import com.example.invoiceflow.audit.AuditLogRepository;
import com.example.invoiceflow.audit.AuditLogService;
import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.InvoiceStatus;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class UserAccountDeletionIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserAccountDeletionService deletionService;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private AuditLogService auditLogService;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    @MockitoBean private StorageService storageService;

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
        clientRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setEmail("byebye@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setFirstName("Bye");
        user.setLastName("User");
        user.setEmailVerified(true);
        userRepository.save(user);

        Client client = new Client();
        client.setUser(user);
        client.setName("Acme");
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
        Set<InvoiceLine> lines = new LinkedHashSet<>();
        lines.add(il);
        invoice.setLines(lines);
        invoiceRepository.save(invoice);

        auditLogService.recordForEmail(AuditAction.LOGIN_SUCCESS, user.getEmail(),
                "User", user.getId().toString(), null);

        token = jwtService.generateToken("byebye@example.com", Role.USER);
    }

    @Test
    void deleteAccount_cascadesAllUserData() {
        deletionService.deleteAccount("byebye@example.com", "Password1");

        assertThat(userRepository.findByEmail("byebye@example.com")).isEmpty();
        assertThat(clientRepository.findAll()).isEmpty();
        assertThat(invoiceRepository.findAll()).isEmpty();
    }

    @Test
    void deleteAccount_scrubsUserAuditLogsAndRecordsAccountDeleted() {
        deletionService.deleteAccount("byebye@example.com", "Password1");

        List<AuditLog> remaining = auditLogRepository.findByActorEmailOrderByOccurredAtDesc("byebye@example.com");
        assertThat(remaining).isEmpty();

        List<AuditLog> all = auditLogRepository.findAll();
        assertThat(all)
                .extracting(AuditLog::getAction)
                .containsExactly(AuditAction.ACCOUNT_DELETED);
        assertThat(all.get(0).getActorEmail()).isNull();
    }

    @Test
    void deleteAccount_wrongPassword_throwsAndKeepsData() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> deletionService.deleteAccount("byebye@example.com", "Wrong!"))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);

        assertThat(userRepository.findByEmail("byebye@example.com")).isPresent();
        assertThat(invoiceRepository.findAll()).hasSize(1);
    }

    @Test
    void endpoint_returns204OnCorrectPassword() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password1\"}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByEmail("byebye@example.com")).isEmpty();
    }

    @Test
    void endpoint_returns401OnWrongPassword() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Wrong!\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(userRepository.findByEmail("byebye@example.com")).isPresent();
    }

    @Test
    void endpoint_withoutToken_returns403() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password1\"}"))
                .andExpect(status().isForbidden());
    }
}
