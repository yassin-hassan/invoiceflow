package com.example.invoiceflow.audit;

import com.example.invoiceflow.PostgresTestContainer;
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

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuditLogIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    @MockitoBean private JavaMailSender mailSender;

    private MockMvc mockMvc;
    private User user;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        Session session = Session.getInstance(new Properties());
        org.mockito.Mockito.when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(session));

        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setEmail("alice@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setFirstName("Alice");
        user.setLastName("Martin");
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Test
    void login_badPassword_persistsLoginFailedRow() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "password": "WrongPassword1"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        List<AuditLog> entries = auditLogRepository.findAll();
        assertThat(entries).hasSize(1);
        AuditLog row = entries.getFirst();
        assertThat(row.getAction()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(row.getActorEmail()).isEqualTo("alice@example.com");
        assertThat(row.getActorUserId()).isEqualTo(user.getId());
        assertThat(row.getDetails()).containsEntry("reason", "bad_password");
        assertThat(row.getOccurredAt()).isNotNull();
    }

    @Test
    void login_unknownUser_persistsLoginFailedWithoutActorUserId() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "ghost@example.com",
                                  "password": "Password1"
                                }
                                """))
                .andExpect(status().isUnauthorized());

        List<AuditLog> entries = auditLogRepository.findAll();
        assertThat(entries).hasSize(1);
        AuditLog row = entries.getFirst();
        assertThat(row.getAction()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(row.getActorEmail()).isEqualTo("ghost@example.com");
        assertThat(row.getActorUserId()).isNull();
        assertThat(row.getDetails()).containsEntry("reason", "unknown_user");
    }

    @Test
    void login_validCredentials_persistsLoginSuccessRow() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "password": "Password1"
                                }
                                """))
                .andExpect(status().isOk());

        List<AuditLog> entries = auditLogRepository.findAll();
        assertThat(entries).hasSize(1);
        AuditLog row = entries.getFirst();
        assertThat(row.getAction()).isEqualTo(AuditAction.LOGIN_SUCCESS);
        assertThat(row.getResourceType()).isEqualTo("User");
        assertThat(row.getResourceId()).isEqualTo(user.getId().toString());
        assertThat(row.getActorEmail()).isEqualTo("alice@example.com");
        assertThat(row.getActorUserId()).isEqualTo(user.getId());
    }

    @Test
    void forgotPassword_persistsPasswordResetRequestedRow() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com"
                                }
                                """))
                .andExpect(status().isOk());

        List<AuditLog> entries = auditLogRepository.findAll();
        assertThat(entries).hasSize(1);
        AuditLog row = entries.getFirst();
        assertThat(row.getAction()).isEqualTo(AuditAction.PASSWORD_RESET_REQUESTED);
        assertThat(row.getActorEmail()).isEqualTo("alice@example.com");
        assertThat(row.getActorUserId()).isEqualTo(user.getId());
        assertThat(row.getResourceType()).isEqualTo("User");
        assertThat(row.getResourceId()).isEqualTo(user.getId().toString());
    }

    @Test
    void forgotPassword_unknownEmail_persistsNothing() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "ghost@example.com"
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findAll()).isEmpty();
    }
}
