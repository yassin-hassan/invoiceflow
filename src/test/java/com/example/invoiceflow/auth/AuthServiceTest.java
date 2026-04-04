package com.example.invoiceflow.auth;

import com.example.invoiceflow.auth.dto.LoginRequest;
import com.example.invoiceflow.exception.AccountLockedException;
import com.example.invoiceflow.exception.EmailNotVerifiedException;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AccountVerificationRepository verificationRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed");
        user.setEmailVerified(true);
        user.setFailedAttempts(0);
    }

    private LoginRequest loginRequest(String email, String password) {
        var request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    // --- login ---

    @Test
    void login_validCredentials_returnsToken() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
        when(jwtService.generateToken("test@example.com")).thenReturn("jwt-token");

        var response = authService.login(loginRequest("test@example.com", "password"));

        assertThat(response.getToken()).isEqualTo("jwt-token");
    }

    @Test
    void login_unknownEmail_throwsBadCredentials() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest("unknown@example.com", "password")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("test@example.com", "wrongpass")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_wrongPassword_incrementsFailedAttempts() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "hashed")).thenReturn(false);

        try { authService.login(loginRequest("test@example.com", "wrongpass")); } catch (Exception ignored) {}

        verify(userRepository).save(argThat(u -> u.getFailedAttempts() == 1));
    }

    @Test
    void login_lockedAccount_throwsAccountLocked() {
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(loginRequest("test@example.com", "password")))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void login_unverifiedEmail_throwsEmailNotVerified() {
        user.setEmailVerified(false);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(loginRequest("test@example.com", "password")))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void login_successfulLogin_resetsFailedAttempts() {
        user.setFailedAttempts(3);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
        when(jwtService.generateToken(any())).thenReturn("jwt-token");

        authService.login(loginRequest("test@example.com", "password"));

        verify(userRepository).save(argThat(u -> u.getFailedAttempts() == 0));
    }

    // --- verifyEmail ---

    @Test
    void verifyEmail_validToken_verifiesUser() {
        var verification = new AccountVerification(user, "valid-token", LocalDateTime.now().plusHours(24));
        when(verificationRepository.findByToken("valid-token")).thenReturn(Optional.of(verification));

        authService.verifyEmail("valid-token");

        verify(userRepository).save(argThat(User::isEmailVerified));
        verify(verificationRepository).delete(verification);
    }

    @Test
    void verifyEmail_invalidToken_throwsBadCredentials() {
        when(verificationRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bad-token"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void verifyEmail_expiredToken_throwsBadCredentials() {
        var verification = new AccountVerification(user, "expired-token", LocalDateTime.now().minusHours(1));
        when(verificationRepository.findByToken("expired-token")).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> authService.verifyEmail("expired-token"))
                .isInstanceOf(BadCredentialsException.class);
    }

    // --- resendVerification ---

    @Test
    void resendVerification_unknownEmail_doesNothing() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        authService.resendVerification("unknown@example.com");

        verifyNoInteractions(emailService);
    }

    @Test
    void resendVerification_alreadyVerified_doesNothing() {
        user.setEmailVerified(true);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        authService.resendVerification("test@example.com");

        verifyNoInteractions(emailService);
    }

    @Test
    void resendVerification_unverifiedUser_sendsEmail() {
        user.setEmailVerified(false);
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        authService.resendVerification("test@example.com");

        verify(verificationRepository).deleteByUserId(user.getId());
        verify(verificationRepository).save(any(AccountVerification.class));
        verify(emailService).sendVerificationEmail(eq("test@example.com"), any());
    }
}
