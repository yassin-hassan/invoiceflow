package com.example.invoiceflow.config;

import com.example.invoiceflow.user.Role;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminBootstrap {

    @Value("${app.admin.bootstrap-email:}")
    private String bootstrapEmail;

    @Bean
    ApplicationRunner promoteBootstrapAdmin(UserRepository userRepository) {
        return args -> promote(userRepository);
    }

    @Transactional
    void promote(UserRepository userRepository) {
        if (bootstrapEmail == null || bootstrapEmail.isBlank()) return;

        if (userRepository.existsByRole(Role.ADMIN)) return;

        String email = bootstrapEmail.toLowerCase().trim();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            log.warn("INVOICEFLOW_ADMIN_EMAIL is set to {} but no such user exists yet — skipping promotion.", email);
            return;
        }

        user.setRole(Role.ADMIN);
        userRepository.save(user);
        log.info("Promoted {} to ADMIN via INVOICEFLOW_ADMIN_EMAIL bootstrap.", email);
    }
}
