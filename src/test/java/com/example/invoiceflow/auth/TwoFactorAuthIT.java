package com.example.invoiceflow.auth;

import com.example.invoiceflow.PostgresTestContainer;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.storage.StorageService;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class TwoFactorAuthIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private TwoFactorVerificationRepository twoFactorRepository;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @MockitoBean private SmsService smsService;
    @MockitoBean private StorageService storageService;

    private MockMvc mockMvc;
    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        twoFactorRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User();
        user.setEmail("alice@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setFirstName("Alice");
        user.setLastName("Martin");
        user.setEmailVerified(true);
        userRepository.save(user);

        token = jwtService.generateToken("alice@example.com");
    }

    // --- PUT /api/users/me/2fa/enable ---

    @Test
    void enable2fa_correctPassword_returns204() throws Exception {
        mockMvc.perform(put("/api/users/me/2fa/enable")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "password": "Password1",
                          "phone": "+33612345678"
                        }
                        """))
                .andExpect(status().isNoContent());

        User updated = userRepository.findByEmail("alice@example.com").orElseThrow();
        assert updated.is2faEnabled();
        assert "+33612345678".equals(updated.getTwoFaPhone());
    }

    @Test
    void enable2fa_wrongPassword_returns401() throws Exception {
        mockMvc.perform(put("/api/users/me/2fa/enable")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "password": "WrongPassword1",
                          "phone": "+33612345678"
                        }
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enable2fa_invalidPhone_returns400() throws Exception {
        mockMvc.perform(put("/api/users/me/2fa/enable")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "password": "Password1",
                          "phone": "0612345678"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enable2fa_withoutToken_returns403() throws Exception {
        mockMvc.perform(put("/api/users/me/2fa/enable")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "password": "Password1",
                          "phone": "+33612345678"
                        }
                        """))
                .andExpect(status().isForbidden());
    }

    // --- DELETE /api/users/me/2fa ---

    @Test
    void disable2fa_correctPassword_returns204() throws Exception {
        enableTwoFactor();

        mockMvc.perform(delete("/api/users/me/2fa")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "password": "Password1" }
                        """))
                .andExpect(status().isNoContent());

        User updated = userRepository.findByEmail("alice@example.com").orElseThrow();
        assert !updated.is2faEnabled();
        assert updated.getTwoFaPhone() == null;
    }

    @Test
    void disable2fa_wrongPassword_returns401() throws Exception {
        enableTwoFactor();

        mockMvc.perform(delete("/api/users/me/2fa")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "password": "WrongPassword1" }
                        """))
                .andExpect(status().isUnauthorized());
    }

    // --- Full login flow with 2FA ---

    @Test
    void login_with2faEnabled_returnsRequires2fa() throws Exception {
        enableTwoFactor();

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "alice@example.com",
                          "password": "Password1"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requires2fa").value(true))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void verify2fa_validCode_returnsJwt() throws Exception {
        enableTwoFactor();

        // Login to trigger OTP generation
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "alice@example.com",
                          "password": "Password1"
                        }
                        """))
                .andExpect(status().isOk());

        // Read the OTP code from the database
        User user = userRepository.findByEmail("alice@example.com").orElseThrow();
        TwoFactorVerification verification = twoFactorRepository.findByUserIdAndCode(user.getId(),
                twoFactorRepository.findAll().getFirst().getCode()).orElseThrow();

        // Verify with the code
        mockMvc.perform(post("/api/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "alice@example.com",
                          "code": "%s"
                        }
                        """.formatted(verification.getCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.requires2fa").doesNotExist());
    }

    @Test
    void verify2fa_wrongCode_returns401() throws Exception {
        enableTwoFactor();

        // Login to trigger OTP generation
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "alice@example.com",
                          "password": "Password1"
                        }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "alice@example.com",
                          "code": "000000"
                        }
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verify2fa_unknownEmail_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/2fa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "nobody@example.com",
                          "code": "123456"
                        }
                        """))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private void enableTwoFactor() throws Exception {
        mockMvc.perform(put("/api/users/me/2fa/enable")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "password": "Password1",
                          "phone": "+33612345678"
                        }
                        """))
                .andExpect(status().isNoContent());
    }
}
