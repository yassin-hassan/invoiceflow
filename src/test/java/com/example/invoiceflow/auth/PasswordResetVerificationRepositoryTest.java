package com.example.invoiceflow.auth;

import com.example.invoiceflow.PostgresTestContainer;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PasswordResetVerificationRepositoryTest extends PostgresTestContainer {

    @Autowired
    private PasswordResetVerificationRepository passwordResetRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("test@example.com");
        user.setPasswordHash("hashed");
        userRepository.save(user);
    }

    @Test
    void findByToken_existingToken_returnsVerification() {
        passwordResetRepository.save(new PasswordResetVerification(user, "my-token", LocalDateTime.now().plusHours(1)));

        java.util.Optional<PasswordResetVerification> result = passwordResetRepository.findByToken("my-token");

        assertThat(result).isPresent();
        assertThat(result.get().getUser().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void findByToken_unknownToken_returnsEmpty() {
        java.util.Optional<PasswordResetVerification> result = passwordResetRepository.findByToken("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void deleteByUserId_deletesVerification() {
        passwordResetRepository.save(new PasswordResetVerification(user, "my-token", LocalDateTime.now().plusHours(1)));

        passwordResetRepository.deleteByUserId(user.getId());

        assertThat(passwordResetRepository.findByToken("my-token")).isEmpty();
    }
}
