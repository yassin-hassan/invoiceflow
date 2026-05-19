package com.example.invoiceflow.creditnote;

import com.example.invoiceflow.PostgresTestContainer;
import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.InvoiceStatus;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CreditNoteControllerIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private CreditNoteRepository creditNoteRepository;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    @MockitoBean private JavaMailSender mailSender;

    private MockMvc mockMvc;
    private String token;
    private User user;
    private Client client;
    private Invoice sentInvoice;
    private InvoiceLine line1;
    private InvoiceLine line2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        Session session = Session.getInstance(new Properties());
        org.mockito.Mockito.when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(session));

        creditNoteRepository.deleteAll();
        invoiceRepository.deleteAll();
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

        sentInvoice = new Invoice();
        sentInvoice.setUser(user);
        sentInvoice.setClient(client);
        sentInvoice.setNumber("FACT-2026-001");
        sentInvoice.setStatus(InvoiceStatus.SENT);
        sentInvoice.setIssueDate(LocalDate.of(2026, 5, 1));
        sentInvoice.setDueDate(LocalDate.of(2026, 6, 1));
        sentInvoice.setLines(new HashSet<>());
        sentInvoice.setPayments(new HashSet<>());

        line1 = new InvoiceLine();
        line1.setInvoice(sentInvoice);
        line1.setDescription("Web development");
        line1.setQuantity(new BigDecimal("10"));
        line1.setUnitPrice(new BigDecimal("100.00"));
        line1.setVatRate(new BigDecimal("20.00"));
        line1.setSortOrder(0);
        sentInvoice.getLines().add(line1);

        line2 = new InvoiceLine();
        line2.setInvoice(sentInvoice);
        line2.setDescription("Design");
        line2.setQuantity(new BigDecimal("4"));
        line2.setUnitPrice(new BigDecimal("50.00"));
        line2.setVatRate(new BigDecimal("20.00"));
        line2.setSortOrder(1);
        sentInvoice.getLines().add(line2);

        invoiceRepository.save(sentInvoice);

        token = jwtService.generateToken("john.doe@example.com", com.example.invoiceflow.user.Role.USER);
    }

    private String extractId(String json) throws Exception {
        return JsonMapper.builder().build().readTree(json).get("id").asText();
    }

    private String createBodyOneLine(UUID lineId, BigDecimal qty, String reason) {
        return """
                {
                  "reason": "%s",
                  "lines": [
                    { "invoiceLineId": "%s", "quantity": %s }
                  ]
                }
                """.formatted(reason, lineId, qty.toPlainString());
    }

    // --- create ---

    @Test
    void create_validRequest_returns201_savesAsDraftWithNullNumber() throws Exception {
        mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("2.00"), "Wrong qty")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number").isEmpty())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.originalInvoiceId").value(sentInvoice.getId().toString()))
                .andExpect(jsonPath("$.originalInvoiceNumber").value("FACT-2026-001"))
                .andExpect(jsonPath("$.reason").value("Wrong qty"))
                .andExpect(jsonPath("$.clientName").value("Acme Corp"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.subtotalExclVat").value(200.00))
                .andExpect(jsonPath("$.totalVat").value(40.00))
                .andExpect(jsonPath("$.totalInclVat").value(240.00));
    }

    @Test
    void create_invoiceInDraftStatus_returns422() throws Exception {
        sentInvoice.setStatus(InvoiceStatus.DRAFT);
        sentInvoice.setNumber(null);
        invoiceRepository.save(sentInvoice);

        mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "x")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_multipleDraftCreditNotes_allowed() throws Exception {
        mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "first")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "second")))
                .andExpect(status().isCreated());
    }

    @Test
    void create_secondCreditNote_exceedingCumulativeAfterFirstIssued_returns422() throws Exception {
        String first = mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("8.00"), "first")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstId = extractId(first);

        mockMvc.perform(post("/api/credit-notes/" + firstId + "/issue")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("3.00"), "second exceeds")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_lineQuantityExceedsOriginal_returns422() throws Exception {
        mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("99.00"), "too much")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_lineFromOtherInvoice_returns422() throws Exception {
        mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(UUID.randomUUID(), new BigDecimal("1.00"), "x")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_missingReason_returns400() throws Exception {
        mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "lines": [ { "invoiceLineId": "%s", "quantity": 1 } ]
                        }
                        """.formatted(line1.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_emptyLines_returns400() throws Exception {
        mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "reason": "x", "lines": [] }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_invoiceNotOwned_returns404() throws Exception {
        mockMvc.perform(post("/api/invoices/00000000-0000-0000-0000-000000000000/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "x")))
                .andExpect(status().isNotFound());
    }

    // --- update ---

    @Test
    void update_draft_replacesLines() throws Exception {
        String id = extractId(mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "first")))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(put("/api/credit-notes/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "reason": "Updated",
                          "lines": [
                            { "invoiceLineId": "%s", "quantity": 3 }
                          ]
                        }
                        """.formatted(line2.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Updated"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].invoiceLineId").value(line2.getId().toString()))
                .andExpect(jsonPath("$.lines[0].quantity").value(3.00));
    }

    @Test
    void update_issued_returns422() throws Exception {
        String id = extractId(mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "x")))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/credit-notes/" + id + "/issue")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/credit-notes/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "reason": "Updated",
                          "lines": [ { "invoiceLineId": "%s", "quantity": 1 } ]
                        }
                        """.formatted(line1.getId())))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- delete ---

    @Test
    void delete_draft_returns204() throws Exception {
        String id = extractId(mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "x")))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(delete("/api/credit-notes/" + id)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/credit-notes")
                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void delete_issued_returns422() throws Exception {
        String id = extractId(mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "x")))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/credit-notes/" + id + "/issue")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/credit-notes/" + id)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- issue ---

    @Test
    void issue_draft_assignsAvNumberAndIssuedAt() throws Exception {
        String id = extractId(mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "x")))
                .andReturn().getResponse().getContentAsString());

        int year = LocalDate.now().getYear();
        mockMvc.perform(post("/api/credit-notes/" + id + "/issue")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.number").value(String.format("AV-%d-001", year)))
                .andExpect(jsonPath("$.issuedAt").isNotEmpty());
    }

    @Test
    void issue_alreadyIssued_returns422() throws Exception {
        String id = extractId(mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "x")))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/credit-notes/" + id + "/issue")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/credit-notes/" + id + "/issue")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- cross-link in invoice response ---

    @Test
    void invoiceResponse_includesCreditNoteIdAndNumberAfterIssue() throws Exception {
        String cnId = extractId(mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "x")))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/credit-notes/" + cnId + "/issue")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/invoices/" + sentInvoice.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditNotes.length()").value(1))
                .andExpect(jsonPath("$.creditNotes[0].id").value(cnId))
                .andExpect(jsonPath("$.creditNotes[0].number").isNotEmpty())
                .andExpect(jsonPath("$.creditNotes[0].status").value("ISSUED"))
                .andExpect(jsonPath("$.creditNotes[0].totalInclVat").value(120.00))
                .andExpect(jsonPath("$.creditNoteTotalInclVat").value(120.00));
    }

    @Test
    void invoiceResponse_creditNoteTotal_isNullWhileCreditNoteIsDraft() throws Exception {
        mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "x")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/invoices/" + sentInvoice.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditNotes.length()").value(1))
                .andExpect(jsonPath("$.creditNotes[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.creditNoteTotalInclVat").isEmpty());
    }

    @Test
    void invoiceResponse_sumsIssuedCreditNoteTotalsAndExcludesDrafts() throws Exception {
        String firstId = extractId(mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "first")))
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/credit-notes/" + firstId + "/issue")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String secondId = extractId(mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("2.00"), "second")))
                .andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/credit-notes/" + secondId + "/issue")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "draft only")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/invoices/" + sentInvoice.getId())
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditNotes.length()").value(3))
                .andExpect(jsonPath("$.creditNoteTotalInclVat").value(360.00));
    }

    // --- list + get ---

    @Test
    void list_returnsUsersCreditNotes() throws Exception {
        mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "x")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/credit-notes")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void get_otherUsersCreditNote_returns404() throws Exception {
        String id = extractId(mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "x")))
                .andReturn().getResponse().getContentAsString());

        User other = new User();
        other.setEmail("jane@example.com");
        other.setPasswordHash(passwordEncoder.encode("Password1"));
        other.setFirstName("Jane");
        other.setLastName("Roe");
        other.setEmailVerified(true);
        userRepository.save(other);
        String otherToken = jwtService.generateToken("jane@example.com", com.example.invoiceflow.user.Role.USER);

        mockMvc.perform(get("/api/credit-notes/" + id)
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    // --- pdf ---

    @Test
    void downloadPdf_existingCreditNote_returnsPdf() throws Exception {
        String id = extractId(mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "x")))
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/credit-notes/" + id + "/issue")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        byte[] body = mockMvc.perform(get("/api/credit-notes/" + id + "/pdf")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).isNotEmpty();
        assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");
    }

    @Test
    void downloadPdf_otherUsersCreditNote_returns404() throws Exception {
        String id = extractId(mockMvc.perform(post("/api/invoices/" + sentInvoice.getId() + "/credit-notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBodyOneLine(line1.getId(), new BigDecimal("1.00"), "x")))
                .andReturn().getResponse().getContentAsString());

        User other = new User();
        other.setEmail("jane@example.com");
        other.setPasswordHash(passwordEncoder.encode("Password1"));
        other.setFirstName("Jane");
        other.setLastName("Roe");
        other.setEmailVerified(true);
        userRepository.save(other);
        String otherToken = jwtService.generateToken("jane@example.com", com.example.invoiceflow.user.Role.USER);

        mockMvc.perform(get("/api/credit-notes/" + id + "/pdf")
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }
}
