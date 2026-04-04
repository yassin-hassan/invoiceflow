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
class AccountVerificationRepositoryTest extends PostgresTestContainer {

    @Autowired
    private AccountVerificationRepository verificationRepository;

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
        verificationRepository.save(new AccountVerification(user, "my-token", LocalDateTime.now().plusHours(24)));

        var result = verificationRepository.findByToken("my-token");

        assertThat(result).isPresent();
        assertThat(result.get().getUser().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void findByToken_unknownToken_returnsEmpty() {
        var result = verificationRepository.findByToken("nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void deleteByUserId_deletesVerification() {
        verificationRepository.save(new AccountVerification(user, "my-token", LocalDateTime.now().plusHours(24)));

        verificationRepository.deleteByUserId(user.getId());

        assertThat(verificationRepository.findByToken("my-token")).isEmpty();
    }
}
