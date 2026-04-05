package com.example.invoiceflow.auth;

import com.example.invoiceflow.auth.dto.LoginRequest;
import com.example.invoiceflow.auth.dto.LoginResponse;
import com.example.invoiceflow.exception.AccountLockedException;
import com.example.invoiceflow.exception.EmailNotVerifiedException;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import com.example.invoiceflow.auth.AccountVerificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AccountVerificationRepository verificationRepository;
    private final PasswordResetVerificationRepository passwordResetRepository;
    private final EmailService emailService;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (isLocked(user)) {
            throw new AccountLockedException();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedAttempt(user);
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        resetFailedAttempts(user);
        String token = jwtService.generateToken(user.getEmail());
        return new LoginResponse(token);
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
            emailService.sendVerificationEmail(user.getEmail(), token);
        });
    }

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email.toLowerCase().trim()).ifPresent(user -> {
            passwordResetRepository.deleteByUserId(user.getId());
            String token = UUID.randomUUID().toString();
            passwordResetRepository.save(new PasswordResetVerification(user, token, LocalDateTime.now().plusHours(1)));
            emailService.sendPasswordResetEmail(user.getEmail(), token);
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
