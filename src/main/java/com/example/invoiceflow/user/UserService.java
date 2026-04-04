package com.example.invoiceflow.user;

import com.example.invoiceflow.auth.AccountVerification;
import com.example.invoiceflow.auth.AccountVerificationRepository;
import com.example.invoiceflow.auth.EmailService;
import com.example.invoiceflow.exception.EmailAlreadyExistsException;
import com.example.invoiceflow.user.dto.CreateUserRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AccountVerificationRepository verificationRepository;
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
}
