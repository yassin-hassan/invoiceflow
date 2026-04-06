package com.example.invoiceflow.user;

import com.example.invoiceflow.auth.AccountVerification;
import com.example.invoiceflow.auth.AccountVerificationRepository;
import com.example.invoiceflow.auth.EmailService;
import com.example.invoiceflow.auth.TwoFactorVerificationRepository;
import com.example.invoiceflow.auth.dto.Disable2faRequest;
import com.example.invoiceflow.auth.dto.Enable2faRequest;
import com.example.invoiceflow.exception.EmailAlreadyExistsException;
import com.example.invoiceflow.user.dto.CreateUserRequest;
import com.example.invoiceflow.storage.StorageService;
import com.example.invoiceflow.user.dto.ChangePasswordRequest;
import com.example.invoiceflow.user.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StorageService storageService;
    private final AccountVerificationRepository verificationRepository;
    private final TwoFactorVerificationRepository twoFactorRepository;
    private final EmailService emailService;

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public User createUser(CreateUserRequest request) {
        String email = request.getEmail().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        userRepository.save(user);

        String token = UUID.randomUUID().toString();
        verificationRepository.save(new AccountVerification(user, token, LocalDateTime.now().plusHours(24)));
        emailService.sendVerificationEmail(email, token);

        return user;
    }

    @Transactional
    public User updateProfile(String email, UpdateProfileRequest request) {
        User user = getByEmail(email);

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getCompanyName() != null) user.setCompanyName(request.getCompanyName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getVatNumber() != null) user.setVatNumber(request.getVatNumber());
        if (request.getPreferredLanguage() != null) user.setPreferredLanguage(request.getPreferredLanguage());

        if (request.getBillingAddress() != null) {
            UpdateProfileRequest.AddressRequest addrReq = request.getBillingAddress();
            Address address = user.getBillingAddress() != null ? user.getBillingAddress() : new Address();
            if (addrReq.getStreet() != null) address.setStreet(addrReq.getStreet());
            if (addrReq.getPostalCode() != null) address.setPostalCode(addrReq.getPostalCode());
            if (addrReq.getCity() != null) address.setCity(addrReq.getCity());
            if (addrReq.getCountry() != null) address.setCountry(addrReq.getCountry());
            user.setBillingAddress(address);
        }

        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = getByEmail(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void enable2fa(String email, Enable2faRequest request) {
        User user = getByEmail(email);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setTwoFaPhone(request.getPhone());
        user.set2faEnabled(true);
        userRepository.save(user);
    }

    @Transactional
    public void disable2fa(String email, Disable2faRequest request) {
        User user = getByEmail(email);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.set2faEnabled(false);
        user.setTwoFaPhone(null);
        twoFactorRepository.deleteByUserId(user.getId());
        userRepository.save(user);
    }

    @Transactional
    public User updateLogo(String email, MultipartFile file) {
        User user = getByEmail(email);

        if (user.getLogoUrl() != null) {
            String contentType = user.getLogoUrl().endsWith(".png") ? "image/png" : "image/jpeg";
            storageService.deleteLogo(user.getId(), contentType);
        }

        String logoUrl = storageService.uploadLogo(user.getId(), file);
        user.setLogoUrl(logoUrl);
        return userRepository.save(user);
    }
}
