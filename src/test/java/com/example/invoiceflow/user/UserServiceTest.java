package com.example.invoiceflow.user;

import com.example.invoiceflow.auth.AccountVerificationRepository;
import com.example.invoiceflow.auth.EmailService;
import com.example.invoiceflow.storage.StorageService;
import com.example.invoiceflow.user.dto.ChangePasswordRequest;
import com.example.invoiceflow.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    @Mock private StorageService storageService;
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

    // --- changePassword ---

    @Test
    void changePassword_correctCurrentPassword_updatesHash() {
        user.setPasswordHash("old-hashed");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPassword1", "old-hashed")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword1")).thenReturn("new-hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPassword1");
        request.setNewPassword("NewPassword1");

        userService.changePassword("test@example.com", request);

        verify(userRepository).save(argThat(u -> u.getPasswordHash().equals("new-hashed")));
    }

    @Test
    void changePassword_wrongCurrentPassword_throwsBadCredentials() {
        user.setPasswordHash("old-hashed");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword1", "old-hashed")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("WrongPassword1");
        request.setNewPassword("NewPassword1");

        assertThatThrownBy(() -> userService.changePassword("test@example.com", request))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);

        verify(userRepository, never()).save(any());
    }

    // --- updateLogo ---

    @Test
    void updateLogo_noExistingLogo_uploadsAndSavesUrl() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(storageService.uploadLogo(any(), any())).thenReturn("https://bucket.s3.eu-west-3.amazonaws.com/logos/uuid.jpg");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/jpeg", new byte[100]);

        User updated = userService.updateLogo("test@example.com", file);

        assertThat(updated.getLogoUrl()).isEqualTo("https://bucket.s3.eu-west-3.amazonaws.com/logos/uuid.jpg");
        verify(storageService).uploadLogo(user.getId(), file);
    }

    @Test
    void updateLogo_existingJpegLogo_deletesOldBeforeUploading() {
        user.setLogoUrl("https://bucket.s3.eu-west-3.amazonaws.com/logos/uuid.jpg");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(storageService.uploadLogo(any(), any())).thenReturn("https://bucket.s3.eu-west-3.amazonaws.com/logos/uuid.jpg");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/jpeg", new byte[100]);

        userService.updateLogo("test@example.com", file);

        verify(storageService).deleteLogo(user.getId(), "image/jpeg");
        verify(storageService).uploadLogo(user.getId(), file);
    }

    @Test
    void updateLogo_existingPngLogo_deletesWithPngContentType() {
        user.setLogoUrl("https://bucket.s3.eu-west-3.amazonaws.com/logos/uuid.png");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(storageService.uploadLogo(any(), any())).thenReturn("https://bucket.s3.eu-west-3.amazonaws.com/logos/uuid.png");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[100]);

        userService.updateLogo("test@example.com", file);

        verify(storageService).deleteLogo(user.getId(), "image/png");
    }
}
