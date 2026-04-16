package com.example.invoiceflow.quote;

import com.example.invoiceflow.PostgresTestContainer;
import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
class QuoteControllerIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private QuoteRepository quoteRepository;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private String token;
    private User user;
    private Client client;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        quoteRepository.deleteAll();
        clientRepository.deleteAll();
        userRepository.deleteAll();

        user = new User();
        user.setEmail("john.doe@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmailVerified(true);
        userRepository.save(user);

        client = new Client();
        client.setUser(user);
        client.setName("Acme Corp");
        client.setEmail("acme@example.com");
        clientRepository.save(client);

        token = jwtService.generateToken("john.doe@example.com");
    }

    private String validQuoteJson() {
        return """
                {
                  "clientId": "%s",
                  "lines": [
                    {
                      "description": "Web development",
                      "quantity": 10,
                      "unitPrice": 85.00,
                      "vatRate": 20.00
                    }
                  ]
                }
                """.formatted(client.getId());
    }

    private String extractId(String json) throws Exception {
        return JsonMapper.builder().build().readTree(json).get("id").asText();
    }

    // --- GET /api/quotes ---

    @Test
    void getQuotes_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/quotes")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getQuotes_withoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/quotes"))
                .andExpect(status().isForbidden());
    }

    // --- POST /api/quotes ---

    @Test
    void createQuote_validRequest_returns201WithGeneratedNumber() throws Exception {
        mockMvc.perform(post("/api/quotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validQuoteJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("DEV-2026-001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.clientId").value(client.getId().toString()))
                .andExpect(jsonPath("$.clientName").value("Acme Corp"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.subtotalExclVat").value(850.00))
                .andExpect(jsonPath("$.totalVat").value(170.00))
                .andExpect(jsonPath("$.totalInclVat").value(1020.00))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createQuote_missingClientId_returns400() throws Exception {
        mockMvc.perform(post("/api/quotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "lines": [
                            { "description": "Work", "quantity": 1, "unitPrice": 100, "vatRate": 20 }
                          ]
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createQuote_emptyLines_returns400() throws Exception {
        mockMvc.perform(post("/api/quotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "clientId": "%s", "lines": [] }
                        """.formatted(client.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createQuote_defaultDates_setCorrectly() throws Exception {
        mockMvc.perform(post("/api/quotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validQuoteJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.issueDate").exists())
                .andExpect(jsonPath("$.expiryDate").exists());
    }

    @Test
    void createQuote_sequentialNumbering() throws Exception {
        mockMvc.perform(post("/api/quotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validQuoteJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("DEV-2026-001"));

        mockMvc.perform(post("/api/quotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validQuoteJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("DEV-2026-002"));
    }

    // --- GET /api/quotes/{id} ---

    @Test
    void getQuote_existingQuote_returns200() throws Exception {
        String response = mockMvc.perform(post("/api/quotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validQuoteJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(get("/api/quotes/" + id)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getQuote_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/quotes/00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // --- PUT /api/quotes/{id} ---

    @Test
    void updateQuote_draftQuote_updatesFields() throws Exception {
        String response = mockMvc.perform(post("/api/quotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validQuoteJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(put("/api/quotes/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "clientId": "%s",
                          "notes": "Updated notes",
                          "lines": [
                            { "description": "Design work", "quantity": 5, "unitPrice": 100.00, "vatRate": 21.00 }
                          ]
                        }
                        """.formatted(client.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value("Updated notes"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].description").value("Design work"));
    }

    @Test
    void updateQuote_sentQuote_returns422() throws Exception {
        String response = mockMvc.perform(post("/api/quotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validQuoteJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(patch("/api/quotes/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "status": "SENT" }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/quotes/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "clientId": "%s",
                          "notes": "Trying to edit",
                          "lines": [
                            { "description": "Work", "quantity": 1, "unitPrice": 100, "vatRate": 20 }
                          ]
                        }
                        """.formatted(client.getId())))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- PATCH /api/quotes/{id}/status ---

    @Test
    void updateStatus_draftToSent_succeeds() throws Exception {
        String response = mockMvc.perform(post("/api/quotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validQuoteJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(patch("/api/quotes/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "status": "SENT" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void updateStatus_invalidTransition_returns422() throws Exception {
        String response = mockMvc.perform(post("/api/quotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validQuoteJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(patch("/api/quotes/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "status": "ACCEPTED" }
                        """))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- DELETE /api/quotes/{id} ---

    @Test
    void deleteQuote_draftQuote_returns204() throws Exception {
        String response = mockMvc.perform(post("/api/quotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validQuoteJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(delete("/api/quotes/" + id)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/quotes")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteQuote_sentQuote_returns422() throws Exception {
        String response = mockMvc.perform(post("/api/quotes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validQuoteJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(patch("/api/quotes/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "status": "SENT" }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/quotes/" + id)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void deleteQuote_nonExistentId_returns404() throws Exception {
        mockMvc.perform(delete("/api/quotes/00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
