package com.example.invoiceflow.auth;

import com.example.invoiceflow.audit.AuditAction;
import com.example.invoiceflow.audit.AuditLogService;
import com.example.invoiceflow.auth.dto.LoginRequest;
import com.example.invoiceflow.auth.dto.LoginResponse;
import com.example.invoiceflow.auth.dto.TwoFactorVerifyRequest;
import com.example.invoiceflow.config.I18nConfig;
import com.example.invoiceflow.exception.AccountLockedException;
import com.example.invoiceflow.exception.EmailNotVerifiedException;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS  = 5;
    private static final int LOCK_DURATION_MINUTES = 15;
    private static final int OTP_TTL_MINUTES       = 5;

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccountVerificationRepository verificationRepository;
    private final PasswordResetVerificationRepository passwordResetRepository;
    private final TwoFactorVerificationRepository twoFactorRepository;
    private final EmailService emailService;
    private final SmsService smsService;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            recordLoginFailed(email, "unknown_user");
            throw new BadCredentialsException("Invalid email or password");
        }

        if (isLocked(user)) {
            recordLoginFailed(email, "locked");
            throw new AccountLockedException();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedAttempt(user);
            recordLoginFailed(email, "bad_password");
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!user.isEmailVerified()) {
            recordLoginFailed(email, "email_not_verified");
            throw new EmailNotVerifiedException();
        }

        resetFailedAttempts(user);

        if (user.is2faEnabled()) {
            return initiate2fa(user);
        }

        auditLogService.recordForEmail(AuditAction.LOGIN_SUCCESS, user.getEmail(), "User", user.getId().toString(), null);
        return LoginResponse.withToken(jwtService.generateToken(user.getEmail(), user.getRole()));
    }

    private void recordLoginFailed(String email, String reason) {
        auditLogService.recordIndependentForEmail(
                AuditAction.LOGIN_FAILED, email, null, null, Map.of("reason", reason));
    }

    @Transactional
    LoginResponse initiate2fa(User user) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        twoFactorRepository.deleteByUserId(user.getId());
        twoFactorRepository.flush();
        twoFactorRepository.save(new TwoFactorVerification(user, code, LocalDateTime.now().plusMinutes(OTP_TTL_MINUTES)));
        smsService.sendOtpSms(user.getTwoFaPhone(), code);
        return LoginResponse.requires2fa();
    }

    @Transactional
    public LoginResponse verifyTwoFactor(TwoFactorVerifyRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired code"));

        TwoFactorVerification verification = twoFactorRepository
                .findByUserIdAndCode(user.getId(), request.getCode())
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired code"));

        if (verification.isExpired()) {
            throw new BadCredentialsException("Invalid or expired code");
        }

        twoFactorRepository.delete(verification);
        auditLogService.recordForEmail(AuditAction.LOGIN_SUCCESS, user.getEmail(), "User", user.getId().toString(), Map.of("via", "2fa"));
        return LoginResponse.withToken(jwtService.generateToken(user.getEmail(), user.getRole()));
    }

    @Transactional
    public void verifyEmail(String token) {
        AccountVerification verification = verificationRepository.findByToken(token)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired verification token"));

        if (verification.isExpired()) {
            throw new BadCredentialsException("Invalid or expired verification token");
        }

        User user = verification.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        verificationRepository.delete(verification);
    }

    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmail(email.toLowerCase().trim()).ifPresent(user -> {
            if (user.isEmailVerified()) return;
            verificationRepository.deleteByUserId(user.getId());
            String token = UUID.randomUUID().toString();
            verificationRepository.save(new AccountVerification(user, token, LocalDateTime.now().plusHours(24)));
            emailService.sendVerificationEmail(user.getEmail(), token, I18nConfig.toLocale(user.getPreferredLanguage()));
        });
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email.toLowerCase().trim()).ifPresent(user -> {
            passwordResetRepository.deleteByUserId(user.getId());
            String token = UUID.randomUUID().toString();
            passwordResetRepository.save(new PasswordResetVerification(user, token, LocalDateTime.now().plusHours(1)));
            emailService.sendPasswordResetEmail(user.getEmail(), token, I18nConfig.toLocale(user.getPreferredLanguage()));
            auditLogService.recordIndependentForEmail(
                    AuditAction.PASSWORD_RESET_REQUESTED, user.getEmail(),
                    "User", user.getId().toString(), null);
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetVerification reset = passwordResetRepository.findByToken(token)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired password reset token"));

        if (reset.isExpired()) {
            throw new BadCredentialsException("Invalid or expired password reset token");
        }

        User user = reset.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setEmailVerified(true);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        passwordResetRepository.delete(reset);
    }

    private boolean isLocked(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private void handleFailedAttempt(User user) {
        int attempts = user.getFailedAttempts() + 1;
        user.setFailedAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
        }
        userRepository.save(user);
    }

    private void resetFailedAttempts(User user) {
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
    }
}
