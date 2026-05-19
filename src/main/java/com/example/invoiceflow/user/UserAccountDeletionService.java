package com.example.invoiceflow.user;

import com.example.invoiceflow.audit.AuditAction;
import com.example.invoiceflow.audit.AuditLogRepository;
import com.example.invoiceflow.audit.AuditLogService;
import com.example.invoiceflow.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountDeletionService.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public void deleteAccount(String email, String currentPassword) {
        User user = userService.getByEmail(email);

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        if (user.getLogoUrl() != null) {
            try {
                String contentType = user.getLogoUrl().endsWith(".png") ? "image/png" : "image/jpeg";
                storageService.deleteLogo(user.getId(), contentType);
            } catch (RuntimeException ex) {
                log.warn("Failed to delete logo for user {} during account deletion: {}",
                        user.getId(), ex.getMessage());
            }
        }

        auditLogRepository.deleteByActorEmail(user.getEmail());
        userRepository.delete(user);

        auditLogService.recordAnonymous(AuditAction.ACCOUNT_DELETED, "User", user.getId().toString(), null);
    }
}
