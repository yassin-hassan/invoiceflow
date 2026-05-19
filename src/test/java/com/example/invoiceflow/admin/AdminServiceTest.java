package com.example.invoiceflow.admin;

import com.example.invoiceflow.audit.AuditAction;
import com.example.invoiceflow.audit.AuditLogService;
import com.example.invoiceflow.auth.EmailService;
import com.example.invoiceflow.auth.PasswordResetVerification;
import com.example.invoiceflow.auth.PasswordResetVerificationRepository;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.exception.ResourceNotFoundException;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.PaymentRepository;
import com.example.invoiceflow.user.Role;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PasswordResetVerificationRepository passwordResetRepository;
    @Mock private EmailService emailService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private AdminService service;

    private User target;
    private User admin;

    @BeforeEach
    void setUp() {
        target = new User();
        target.setId(UUID.randomUUID());
        target.setEmail("user@example.com");
        target.setRole(Role.USER);
        target.setActive(true);
        target.setPreferredLanguage("FR");

        admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setEmail("admin@example.com");
        admin.setRole(Role.ADMIN);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUser_unknownId_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUser(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_disablesAndEmitsAuditWithFromTo() {
        authenticate("admin@example.com");
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(paymentRepository.sumAmountByUserId(target.getId())).thenReturn(new BigDecimal("42.00"));

        service.updateStatus(target.getId(), false);

        assertThat(target.isActive()).isFalse();
        verify(userRepository).save(target);

        ArgumentCaptor<Map<String, Object>> detailsCaptor = captureDetails();
        verify(auditLogService).record(
                eq(AuditAction.ADMIN_USER_STATUS_CHANGED),
                eq("User"), eq(target.getId().toString()), detailsCaptor.capture());
        Map<String, Object> details = detailsCaptor.getValue();
        assertThat(details).containsEntry("from", true)
                .containsEntry("to", false)
                .containsEntry("targetEmail", "user@example.com");
    }

    @Test
    void updateStatus_selfTarget_throws400AndDoesNotSave() {
        authenticate("admin@example.com");
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateStatus(admin.getId(), false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        verify(userRepository, never()).save(any());
        verify(auditLogService, never()).record(any(), any(), any(), any());
    }

    @Test
    void updateStatus_selfTarget_isCaseInsensitive() {
        authenticate("ADMIN@example.com");
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateStatus(admin.getId(), false))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void updateRole_promotesAndEmitsAuditWithEnumNames() {
        authenticate("admin@example.com");
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.updateRole(target.getId(), Role.ADMIN);

        assertThat(target.getRole()).isEqualTo(Role.ADMIN);

        ArgumentCaptor<Map<String, Object>> detailsCaptor = captureDetails();
        verify(auditLogService).record(
                eq(AuditAction.ADMIN_USER_ROLE_CHANGED),
                eq("User"), eq(target.getId().toString()), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue())
                .containsEntry("from", "USER")
                .containsEntry("to", "ADMIN")
                .containsEntry("targetEmail", "user@example.com");
    }

    @Test
    void updateRole_selfTarget_throws400() {
        authenticate("admin@example.com");
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateRole(admin.getId(), Role.USER))
                .isInstanceOf(ResponseStatusException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void triggerPasswordReset_savesTokenAndSendsEmailAndEmitsAudit() {
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        service.triggerPasswordReset(target.getId());

        verify(passwordResetRepository).deleteByUserId(target.getId());

        ArgumentCaptor<PasswordResetVerification> verCaptor = ArgumentCaptor.forClass(PasswordResetVerification.class);
        verify(passwordResetRepository).save(verCaptor.capture());
        PasswordResetVerification saved = verCaptor.getValue();
        assertThat(saved.getUser()).isSameAs(target);
        assertThat(saved.getToken()).isNotBlank();
        assertThat(saved.getExpiresAt()).isAfter(java.time.LocalDateTime.now().plusMinutes(50));

        verify(emailService).sendPasswordResetEmail(eq("user@example.com"), eq(saved.getToken()), any());

        ArgumentCaptor<Map<String, Object>> detailsCaptor = captureDetails();
        verify(auditLogService).record(
                eq(AuditAction.ADMIN_USER_PASSWORD_RESET_SENT),
                eq("User"), eq(target.getId().toString()), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue()).containsEntry("targetEmail", "user@example.com");
    }

    @Test
    void triggerPasswordReset_unknownUser_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.triggerPasswordReset(id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(passwordResetRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> captureDetails() {
        return ArgumentCaptor.forClass(Map.class);
    }

    private void authenticate(String email) {
        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername(email)
                .password("x")
                .authorities(List.of())
                .build();
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
