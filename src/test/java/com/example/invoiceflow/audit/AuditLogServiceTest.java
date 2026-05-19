package com.example.invoiceflow.audit;

import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private RequestContext requestContext;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(auditLogRepository, userRepository, requestContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void record_resolvesActorFromSecurityContext() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        user.setEmail("actor@example.com");
        when(userRepository.findByEmail("actor@example.com")).thenReturn(Optional.of(user));
        when(requestContext.ipAddress()).thenReturn("10.0.0.1");
        when(requestContext.userAgent()).thenReturn("ua");
        authenticate("actor@example.com");

        service.record(AuditAction.INVOICE_SENT, "Invoice", "abc", Map.of("k", "v"));

        AuditLog saved = capturedSave();
        assertThat(saved.getActorUserId()).isEqualTo(id);
        assertThat(saved.getActorEmail()).isEqualTo("actor@example.com");
        assertThat(saved.getAction()).isEqualTo(AuditAction.INVOICE_SENT);
        assertThat(saved.getResourceType()).isEqualTo("Invoice");
        assertThat(saved.getResourceId()).isEqualTo("abc");
        assertThat(saved.getIpAddress()).isEqualTo("10.0.0.1");
        assertThat(saved.getUserAgent()).isEqualTo("ua");
        assertThat(saved.getDetails()).containsEntry("k", "v");
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    void record_anonymousContext_leavesActorNull() {
        service.record(AuditAction.STRIPE_PAYMENT_CONFIRMED, "Invoice", "abc", null);

        AuditLog saved = capturedSave();
        assertThat(saved.getActorUserId()).isNull();
        assertThat(saved.getActorEmail()).isNull();
    }

    @Test
    void recordIndependentForEmail_setsActorEmailEvenWhenUserMissing() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        service.recordIndependentForEmail(
                AuditAction.LOGIN_FAILED, "Ghost@Example.com", null, null, Map.of("reason", "unknown_user"));

        AuditLog saved = capturedSave();
        assertThat(saved.getActorEmail()).isEqualTo("ghost@example.com");
        assertThat(saved.getActorUserId()).isNull();
        assertThat(saved.getAction()).isEqualTo(AuditAction.LOGIN_FAILED);
        assertThat(saved.getDetails()).containsEntry("reason", "unknown_user");
    }

    @Test
    void recordAnonymous_neverConsultsSecurityContext() {
        authenticate("ignored@example.com");

        service.recordAnonymous(AuditAction.STRIPE_PAYMENT_CONFIRMED, "Invoice", "abc", null);

        AuditLog saved = capturedSave();
        assertThat(saved.getActorEmail()).isNull();
        assertThat(saved.getActorUserId()).isNull();
    }

    private AuditLog capturedSave() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        org.mockito.Mockito.verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
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
