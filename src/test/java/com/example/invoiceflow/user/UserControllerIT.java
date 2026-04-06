package com.example.invoiceflow.user;

import com.example.invoiceflow.PostgresTestContainer;
import com.example.invoiceflow.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class UserControllerIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;

    private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userRepository.deleteAll();

        User user = new User();
        user.setEmail("john.doe@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmailVerified(true);
        userRepository.save(user);

        token = jwtService.generateToken("john.doe@example.com");
    }

    // --- GET /api/users/me ---

    @Test
    void getMe_withValidToken_returnsUserProfile() throws Exception {
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.preferredLanguage").value("FR"));
    }

    @Test
    void getMe_withoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());
    }

    // --- PUT /api/users/me ---

    @Test
    void updateMe_updatesScalarFields() throws Exception {
        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "companyName": "Acme Corp",
                          "phone": "+33600000000",
                          "vatNumber": "FR12345678901",
                          "preferredLanguage": "EN"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Acme Corp"))
                .andExpect(jsonPath("$.phone").value("+33600000000"))
                .andExpect(jsonPath("$.vatNumber").value("FR12345678901"))
                .andExpect(jsonPath("$.preferredLanguage").value("EN"));
    }

    @Test
    void updateMe_withBillingAddress_savesAndReturnsAddress() throws Exception {
        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "billingAddress": {
                            "street": "1 rue de la Paix",
                            "postalCode": "75001",
                            "city": "Paris",
                            "country": "France"
                          }
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingAddress.street").value("1 rue de la Paix"))
                .andExpect(jsonPath("$.billingAddress.postalCode").value("75001"))
                .andExpect(jsonPath("$.billingAddress.city").value("Paris"))
                .andExpect(jsonPath("$.billingAddress.country").value("France"));
    }

    @Test
    void updateMe_partialUpdate_doesNotOverwriteExistingFields() throws Exception {
        // First set a phone number
        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "phone": "+33600000000" }
                        """))
                .andExpect(status().isOk());

        // Then update only companyName — phone should still be there
        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "companyName": "Acme" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Acme"))
                .andExpect(jsonPath("$.phone").value("+33600000000"));
    }

    @Test
    void updateMe_invalidLanguage_returns400() throws Exception {
        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "preferredLanguage": "ES" }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMe_invalidPhone_returns400() throws Exception {
        mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "phone": "not-a-phone" }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateMe_withoutToken_returns403() throws Exception {
        mockMvc.perform(put("/api/users/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "firstName": "Jane" }
                        """))
                .andExpect(status().isForbidden());
    }

    // --- PUT /api/users/me/password ---

    @Test
    void changePassword_correctCurrentPassword_returns204() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "currentPassword": "Password1",
                          "newPassword": "NewPassword1"
                        }
                        """))
                .andExpect(status().isNoContent());
    }

    @Test
    void changePassword_wrongCurrentPassword_returns401() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "currentPassword": "WrongPassword1",
                          "newPassword": "NewPassword1"
                        }
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_weakNewPassword_returns400() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "currentPassword": "Password1",
                          "newPassword": "weak"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_withoutToken_returns403() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "currentPassword": "Password1",
                          "newPassword": "NewPassword1"
                        }
                        """))
                .andExpect(status().isForbidden());
    }
}
