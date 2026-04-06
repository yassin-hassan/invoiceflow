package com.example.invoiceflow.auth;

import com.example.invoiceflow.auth.dto.LoginRequest;
import com.example.invoiceflow.auth.dto.TwoFactorVerifyRequest;
import com.example.invoiceflow.exception.AccountLockedException;
import com.example.invoiceflow.exception.EmailNotVerifiedException;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private PasswordResetVerificationRepository passwordResetRepository;

    @Mock
    private TwoFactorVerificationRepository twoFactorRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private SmsService smsService;

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

    // --- forgotPassword ---

    @Test
    void forgotPassword_unknownEmail_doesNothing() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword("unknown@example.com");

        verifyNoInteractions(emailService);
    }

    @Test
    void forgotPassword_knownEmail_deletesExistingTokenAndSendsEmail() {
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        authService.forgotPassword("test@example.com");

        verify(passwordResetRepository).deleteByUserId(user.getId());
        verify(passwordResetRepository).save(any(PasswordResetVerification.class));
        verify(emailService).sendPasswordResetEmail(eq("test@example.com"), any());
    }

    // --- resetPassword ---

    @Test
    void resetPassword_validToken_updatesPasswordAndVerifiesEmail() {
        PasswordResetVerification reset = new PasswordResetVerification(user, "valid-token", LocalDateTime.now().plusHours(1));
        when(passwordResetRepository.findByToken("valid-token")).thenReturn(Optional.of(reset));
        when(passwordEncoder.encode("NewPassword1")).thenReturn("new-hashed");

        authService.resetPassword("valid-token", "NewPassword1");

        verify(userRepository).save(argThat(u -> u.getPasswordHash().equals("new-hashed") && u.isEmailVerified()));
        verify(passwordResetRepository).delete(reset);
    }

    @Test
    void resetPassword_validToken_clearsAccountLock() {
        user.setFailedAttempts(5);
        user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        PasswordResetVerification reset = new PasswordResetVerification(user, "valid-token", LocalDateTime.now().plusHours(1));
        when(passwordResetRepository.findByToken("valid-token")).thenReturn(Optional.of(reset));
        when(passwordEncoder.encode(any())).thenReturn("new-hashed");

        authService.resetPassword("valid-token", "NewPassword1");

        verify(userRepository).save(argThat(u -> u.getFailedAttempts() == 0 && u.getLockedUntil() == null));
    }

    @Test
    void resetPassword_invalidToken_throwsBadCredentials() {
        when(passwordResetRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bad-token", "NewPassword1"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void resetPassword_expiredToken_throwsBadCredentials() {
        PasswordResetVerification reset = new PasswordResetVerification(user, "expired-token", LocalDateTime.now().minusHours(1));
        when(passwordResetRepository.findByToken("expired-token")).thenReturn(Optional.of(reset));

        assertThatThrownBy(() -> authService.resetPassword("expired-token", "NewPassword1"))
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

    // --- login with 2FA ---

    @Test
    void login_2faEnabled_returnsRequires2fa() {
        user.setId(UUID.randomUUID());
        user.set2faEnabled(true);
        user.setTwoFaPhone("+33612345678");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);

        var response = authService.login(loginRequest("test@example.com", "password"));

        assertThat(response.getRequires2fa()).isTrue();
        assertThat(response.getToken()).isNull();
    }

    @Test
    void login_2faEnabled_sendsSmsWith6DigitCode() {
        user.setId(UUID.randomUUID());
        user.set2faEnabled(true);
        user.setTwoFaPhone("+33612345678");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);

        authService.login(loginRequest("test@example.com", "password"));

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsService).sendOtpSms(eq("+33612345678"), codeCaptor.capture());
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
    }

    @Test
    void login_2faEnabled_savesVerificationRecord() {
        user.setId(UUID.randomUUID());
        user.set2faEnabled(true);
        user.setTwoFaPhone("+33612345678");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);

        authService.login(loginRequest("test@example.com", "password"));

        verify(twoFactorRepository).deleteByUserId(user.getId());
        verify(twoFactorRepository).save(any(TwoFactorVerification.class));
    }

    @Test
    void login_2faDisabled_returnsTokenDirectly() {
        user.set2faEnabled(false);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed")).thenReturn(true);
        when(jwtService.generateToken("test@example.com")).thenReturn("jwt-token");

        var response = authService.login(loginRequest("test@example.com", "password"));

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getRequires2fa()).isNull();
        verifyNoInteractions(smsService);
    }

    // --- verifyTwoFactor ---

    private TwoFactorVerifyRequest twoFactorRequest(String email, String code) {
        var request = new TwoFactorVerifyRequest();
        request.setEmail(email);
        request.setCode(code);
        return request;
    }

    @Test
    void verifyTwoFactor_validCode_returnsToken() {
        user.setId(UUID.randomUUID());
        var verification = new TwoFactorVerification(user, "123456", LocalDateTime.now().plusMinutes(5));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(twoFactorRepository.findByUserIdAndCode(user.getId(), "123456")).thenReturn(Optional.of(verification));
        when(jwtService.generateToken("test@example.com")).thenReturn("jwt-token");

        var response = authService.verifyTwoFactor(twoFactorRequest("test@example.com", "123456"));

        assertThat(response.getToken()).isEqualTo("jwt-token");
        verify(twoFactorRepository).delete(verification);
    }

    @Test
    void verifyTwoFactor_unknownEmail_throwsBadCredentials() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyTwoFactor(twoFactorRequest("unknown@example.com", "123456")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void verifyTwoFactor_wrongCode_throwsBadCredentials() {
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(twoFactorRepository.findByUserIdAndCode(user.getId(), "000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyTwoFactor(twoFactorRequest("test@example.com", "000000")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void verifyTwoFactor_expiredCode_throwsBadCredentials() {
        user.setId(UUID.randomUUID());
        var verification = new TwoFactorVerification(user, "123456", LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(twoFactorRepository.findByUserIdAndCode(user.getId(), "123456")).thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> authService.verifyTwoFactor(twoFactorRequest("test@example.com", "123456")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
