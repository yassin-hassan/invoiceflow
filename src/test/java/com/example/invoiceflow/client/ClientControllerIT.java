package com.example.invoiceflow.client;

import com.example.invoiceflow.PostgresTestContainer;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ClientControllerIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private String token;
    private User user;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        clientRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setEmail("john.doe@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmailVerified(true);
        userRepository.save(user);

        token = jwtService.generateToken("john.doe@example.com");
    }

    // --- GET /api/clients ---

    @Test
    void getClients_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/clients")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getClients_withoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/clients"))
                .andExpect(status().isForbidden());
    }

    // --- POST /api/clients ---

    @Test
    void createClient_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Acme Corp",
                          "email": "acme@example.com",
                          "phone": "+32477000000",
                          "vatNumber": "BE0123456789"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.email").value("acme@example.com"))
                .andExpect(jsonPath("$.vatNumber").value("BE0123456789"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createClient_missingName_returns400() throws Exception {
        mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "email": "acme@example.com" }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createClient_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "name": "Acme Corp", "email": "not-an-email" }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createClient_duplicateEmail_returns409() throws Exception {
        mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "name": "Acme Corp", "email": "acme@example.com" }
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "name": "Acme Corp 2", "email": "acme@example.com" }
                        """))
                .andExpect(status().isConflict());
    }

    // --- GET /api/clients/{id} ---

    @Test
    void getClient_existingClient_returns200() throws Exception {
        String response = mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "name": "Acme Corp", "email": "acme@example.com" }
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asText();

        mockMvc.perform(get("/api/clients/" + id)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"));
    }

    @Test
    void getClient_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/clients/00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // --- PUT /api/clients/{id} ---

    @Test
    void updateClient_validRequest_updatesFields() throws Exception {
        String response = mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "name": "Acme Corp", "email": "acme@example.com" }
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asText();

        mockMvc.perform(put("/api/clients/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "name": "Acme Corporation", "phone": "+32477999999" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corporation"))
                .andExpect(jsonPath("$.phone").value("+32477999999"))
                .andExpect(jsonPath("$.email").value("acme@example.com"));
    }

    // --- DELETE /api/clients/{id} ---

    @Test
    void deleteClient_existingClient_returns204AndSoftDeletes() throws Exception {
        String response = mockMvc.perform(post("/api/clients")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "name": "Acme Corp", "email": "acme@example.com" }
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asText();

        mockMvc.perform(delete("/api/clients/" + id)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // soft deleted — should no longer appear in list
        mockMvc.perform(get("/api/clients")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteClient_nonExistentId_returns404() throws Exception {
        mockMvc.perform(delete("/api/clients/00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
