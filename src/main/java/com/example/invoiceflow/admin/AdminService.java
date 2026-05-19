package com.example.invoiceflow.admin;

import com.example.invoiceflow.admin.dto.AdminUserDetail;
import com.example.invoiceflow.admin.dto.AdminUserListItem;
import com.example.invoiceflow.audit.AuditAction;
import com.example.invoiceflow.audit.AuditLogService;
import com.example.invoiceflow.auth.EmailService;
import com.example.invoiceflow.auth.PasswordResetVerification;
import com.example.invoiceflow.auth.PasswordResetVerificationRepository;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.config.I18nConfig;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.PaymentRepository;
import com.example.invoiceflow.user.Role;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import com.example.invoiceflow.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final int RESET_TOKEN_TTL_HOURS = 1;

    private final UserRepository userRepository;
    private final ClientRepository clientRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PasswordResetVerificationRepository passwordResetRepository;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    public List<AdminUserListItem> listUsers() {
        return userRepository.findAllForAdmin();
    }

    public AdminUserDetail getUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toDetail(user);
    }

    @Transactional
    public AdminUserDetail updateStatus(UUID id, boolean active) {
        User user = loadAndGuardSelf(id);
        boolean previous = user.isActive();
        user.setActive(active);
        userRepository.save(user);

        auditLogService.record(
                AuditAction.ADMIN_USER_STATUS_CHANGED,
                "User", user.getId().toString(),
                Map.of("from", previous, "to", active, "targetEmail", user.getEmail()));

        return toDetail(user);
    }

    @Transactional
    public AdminUserDetail updateRole(UUID id, Role role) {
        User user = loadAndGuardSelf(id);
        Role previous = user.getRole();
        user.setRole(role);
        userRepository.save(user);

        auditLogService.record(
                AuditAction.ADMIN_USER_ROLE_CHANGED,
                "User", user.getId().toString(),
                Map.of("from", previous.name(), "to", role.name(), "targetEmail", user.getEmail()));

        return toDetail(user);
    }

    @Transactional
    public void triggerPasswordReset(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        passwordResetRepository.deleteByUserId(user.getId());
        String token = UUID.randomUUID().toString();
        passwordResetRepository.save(new PasswordResetVerification(
                user, token, LocalDateTime.now().plusHours(RESET_TOKEN_TTL_HOURS)));
        emailService.sendPasswordResetEmail(user.getEmail(), token,
                I18nConfig.toLocale(user.getPreferredLanguage()));

        auditLogService.record(
                AuditAction.ADMIN_USER_PASSWORD_RESET_SENT,
                "User", user.getId().toString(),
                Map.of("targetEmail", user.getEmail()));
    }

    private User loadAndGuardSelf(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getEmail().equalsIgnoreCase(currentActorEmail())) {
            throw new ResponseStatusException(BAD_REQUEST, "Admin cannot modify own account");
        }
        return user;
    }

    private String currentActorEmail() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails ud)) return null;
        return ud.getUsername();
    }

    private AdminUserDetail toDetail(User user) {
        AdminUserDetail dto = new AdminUserDetail();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setCompanyName(user.getCompanyName());
        dto.setPhone(user.getPhone());
        dto.setVatNumber(user.getVatNumber());
        dto.setPreferredLanguage(user.getPreferredLanguage());
        dto.setRole(user.getRole());
        dto.setActive(user.isActive());
        dto.setEmailVerified(user.isEmailVerified());
        dto.setTwoFaEnabled(user.is2faEnabled());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setClientCount(clientRepository.countByUserIdAndActive(user.getId()));
        dto.setInvoiceCount(invoiceRepository.countByUserId(user.getId()));
        BigDecimal total = paymentRepository.sumAmountByUserId(user.getId());
        dto.setTotalRevenue(total == null ? BigDecimal.ZERO : total);
        return dto;
    }
}
