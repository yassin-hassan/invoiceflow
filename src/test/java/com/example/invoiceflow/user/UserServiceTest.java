package com.example.invoiceflow.user;

import com.example.invoiceflow.auth.AccountVerificationRepository;
import com.example.invoiceflow.auth.EmailService;
import com.example.invoiceflow.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @Mock private AccountVerificationRepository verificationRepository;
    @Mock private EmailService emailService;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setPreferredLanguage("FR");
    }

    // --- updateProfile ---

    @Test
    void updateProfile_updatesScalarFields() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFirstName("Jane");
        request.setLastName("Smith");
        request.setCompanyName("NewCo");
        request.setPhone("+33611111111");
        request.setVatNumber("FR99999999999");
        request.setPreferredLanguage("EN");

        User updated = userService.updateProfile("test@example.com", request);

        assertThat(updated.getFirstName()).isEqualTo("Jane");
        assertThat(updated.getLastName()).isEqualTo("Smith");
        assertThat(updated.getCompanyName()).isEqualTo("NewCo");
        assertThat(updated.getPhone()).isEqualTo("+33611111111");
        assertThat(updated.getVatNumber()).isEqualTo("FR99999999999");
        assertThat(updated.getPreferredLanguage()).isEqualTo("EN");
    }

    @Test
    void updateProfile_nullFields_areNotOverwritten() {
        user.setFirstName("Original");
        user.setPhone("+33600000000");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest();
        // firstName and phone are null — should not be overwritten

        User updated = userService.updateProfile("test@example.com", request);

        assertThat(updated.getFirstName()).isEqualTo("Original");
        assertThat(updated.getPhone()).isEqualTo("+33600000000");
    }

    @Test
    void updateProfile_createsNewAddressWhenNoneExists() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest.AddressRequest addrReq = new UpdateProfileRequest.AddressRequest();
        addrReq.setStreet("10 Downing Street");
        addrReq.setPostalCode("SW1A 2AA");
        addrReq.setCity("London");
        addrReq.setCountry("UK");

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setBillingAddress(addrReq);

        User updated = userService.updateProfile("test@example.com", request);

        assertThat(updated.getBillingAddress()).isNotNull();
        assertThat(updated.getBillingAddress().getStreet()).isEqualTo("10 Downing Street");
        assertThat(updated.getBillingAddress().getCity()).isEqualTo("London");
    }

    @Test
    void updateProfile_updatesExistingAddress() {
        Address existing = new Address();
        existing.setStreet("Old Street");
        existing.setCity("Old City");
        user.setBillingAddress(existing);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest.AddressRequest addrReq = new UpdateProfileRequest.AddressRequest();
        addrReq.setCity("New City");

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setBillingAddress(addrReq);

        User updated = userService.updateProfile("test@example.com", request);

        // City updated, street preserved
        assertThat(updated.getBillingAddress().getCity()).isEqualTo("New City");
        assertThat(updated.getBillingAddress().getStreet()).isEqualTo("Old Street");
    }
}
