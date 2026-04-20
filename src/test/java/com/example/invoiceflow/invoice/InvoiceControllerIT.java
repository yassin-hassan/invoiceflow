package com.example.invoiceflow.invoice;

import com.example.invoiceflow.PostgresTestContainer;
import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.quote.Quote;
import com.example.invoiceflow.quote.QuoteRepository;
import com.example.invoiceflow.quote.QuoteStatus;
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

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class InvoiceControllerIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private QuoteRepository quoteRepository;
    @Autowired private InvoiceRepository invoiceRepository;
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

        invoiceRepository.deleteAll();
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

    private String validInvoiceJson() {
        return """
                {
                  "clientId": "%s",
                  "lines": [
                    {
                      "description": "Web development",
                      "quantity": 10,
                      "unitPrice": 100.00,
                      "vatRate": 20.00
                    }
                  ]
                }
                """.formatted(client.getId());
    }

    private String extractId(String json) throws Exception {
        return JsonMapper.builder().build().readTree(json).get("id").asText();
    }

    // --- GET /api/invoices ---

    @Test
    void getInvoices_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/invoices")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getInvoices_withoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isForbidden());
    }

    // --- POST /api/invoices ---

    @Test
    void createInvoice_validRequest_returns201WithGeneratedNumber() throws Exception {
        mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validInvoiceJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("FACT-2026-001"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.clientName").value("Acme Corp"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.subtotalExclVat").value(1000.00))
                .andExpect(jsonPath("$.totalVat").value(200.00))
                .andExpect(jsonPath("$.totalInclVat").value(1200.00))
                .andExpect(jsonPath("$.amountPaid").value(0.00))
                .andExpect(jsonPath("$.amountDue").value(1200.00));
    }

    @Test
    void createInvoice_missingClientId_returns400() throws Exception {
        mockMvc.perform(post("/api/invoices")
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
    void createInvoice_emptyLines_returns400() throws Exception {
        mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "clientId": "%s", "lines": [] }
                        """.formatted(client.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createInvoice_sequentialNumbering() throws Exception {
        mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validInvoiceJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("FACT-2026-001"));

        mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validInvoiceJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("FACT-2026-002"));
    }

    // --- POST /api/quotes/{id}/convert ---

    @Test
    void convertQuote_acceptedQuote_returns201AndMarksConverted() throws Exception {
        Quote quote = new Quote();
        quote.setUser(user);
        quote.setClient(client);
        quote.setNumber("DEV-2026-001");
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setIssueDate(java.time.LocalDate.now());
        quote.setExpiryDate(java.time.LocalDate.now().plusDays(30));
        quote.setLines(new ArrayList<>());

        com.example.invoiceflow.quote.QuoteLine line = new com.example.invoiceflow.quote.QuoteLine();
        line.setQuote(quote);
        line.setDescription("Design work");
        line.setQuantity(new BigDecimal("2"));
        line.setUnitPrice(new BigDecimal("150.00"));
        line.setVatRate(new BigDecimal("20.00"));
        quote.getLines().add(line);
        quoteRepository.save(quote);

        mockMvc.perform(post("/api/quotes/" + quote.getId() + "/convert")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").value("FACT-2026-001"))
                .andExpect(jsonPath("$.quoteId").value(quote.getId().toString()))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.subtotalExclVat").value(300.00))
                .andExpect(jsonPath("$.totalVat").value(60.00))
                .andExpect(jsonPath("$.totalInclVat").value(360.00));
    }

    @Test
    void convertQuote_nonAcceptedQuote_returns422() throws Exception {
        Quote quote = new Quote();
        quote.setUser(user);
        quote.setClient(client);
        quote.setNumber("DEV-2026-001");
        quote.setStatus(QuoteStatus.SENT);
        quote.setIssueDate(java.time.LocalDate.now());
        quote.setExpiryDate(java.time.LocalDate.now().plusDays(30));
        quote.setLines(new ArrayList<>());
        quoteRepository.save(quote);

        mockMvc.perform(post("/api/quotes/" + quote.getId() + "/convert")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- GET /api/invoices/{id} ---

    @Test
    void getInvoice_existingInvoice_returns200() throws Exception {
        String response = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validInvoiceJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(get("/api/invoices/" + id)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void getInvoice_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/invoices/00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // --- PUT /api/invoices/{id} ---

    @Test
    void updateInvoice_draftInvoice_updatesFields() throws Exception {
        String response = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validInvoiceJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(put("/api/invoices/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "clientId": "%s",
                          "paymentTerms": "30 days net",
                          "lines": [
                            { "description": "Consulting", "quantity": 5, "unitPrice": 200.00, "vatRate": 21.00 }
                          ]
                        }
                        """.formatted(client.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentTerms").value("30 days net"))
                .andExpect(jsonPath("$.lines[0].description").value("Consulting"));
    }

    @Test
    void updateInvoice_sentInvoice_returns422() throws Exception {
        String response = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validInvoiceJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(patch("/api/invoices/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "status": "SENT" }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/invoices/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "clientId": "%s",
                          "lines": [
                            { "description": "Work", "quantity": 1, "unitPrice": 100, "vatRate": 20 }
                          ]
                        }
                        """.formatted(client.getId())))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- PATCH /api/invoices/{id}/status ---

    @Test
    void updateStatus_draftToSent_succeeds() throws Exception {
        String response = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validInvoiceJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(patch("/api/invoices/" + id + "/status")
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
        String response = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validInvoiceJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(patch("/api/invoices/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "status": "PAID" }
                        """))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- DELETE /api/invoices/{id} ---

    @Test
    void deleteInvoice_draftInvoice_returns204() throws Exception {
        String response = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validInvoiceJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(delete("/api/invoices/" + id)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/invoices")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteInvoice_nonExistentId_returns404() throws Exception {
        mockMvc.perform(delete("/api/invoices/00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // --- POST /api/invoices/{id}/payments ---

    @Test
    void recordPayment_fullPayment_setsStatusToPaid() throws Exception {
        String response = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validInvoiceJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(patch("/api/invoices/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "status": "SENT" }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/invoices/" + id + "/payments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 1200.00,
                          "method": "bank_transfer",
                          "paidAt": "2026-04-20"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amountPaid").value(1200.00))
                .andExpect(jsonPath("$.amountDue").value(0.00))
                .andExpect(jsonPath("$.payments.length()").value(1));
    }

    @Test
    void recordPayment_partialPayment_setsStatusToPartiallyPaid() throws Exception {
        String response = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validInvoiceJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(patch("/api/invoices/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "status": "SENT" }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/invoices/" + id + "/payments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 500.00,
                          "method": "cash",
                          "paidAt": "2026-04-20"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.amountPaid").value(500.00))
                .andExpect(jsonPath("$.amountDue").value(700.00));
    }

    @Test
    void recordPayment_exceedsBalance_returns409() throws Exception {
        String response = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validInvoiceJson()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = extractId(response);

        mockMvc.perform(patch("/api/invoices/" + id + "/status")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "status": "SENT" }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/invoices/" + id + "/payments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 9999.00,
                          "method": "bank_transfer",
                          "paidAt": "2026-04-20"
                        }
                        """))
                .andExpect(status().isConflict());
    }
}
