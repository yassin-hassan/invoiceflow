package com.example.invoiceflow.product;

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
class ProductControllerIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
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

        productRepository.deleteAll();
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

    // --- GET /api/products ---

    @Test
    void getProducts_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getProducts_withoutToken_returns403() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isForbidden());
    }

    // --- POST /api/products ---

    @Test
    void createProduct_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Widget",
                          "reference": "REF-001",
                          "unitPrice": 99.99,
                          "vatRate": 20.00,
                          "unit": "piece"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Widget"))
                .andExpect(jsonPath("$.reference").value("REF-001"))
                .andExpect(jsonPath("$.unitPrice").value(99.99))
                .andExpect(jsonPath("$.vatRate").value(20.00))
                .andExpect(jsonPath("$.unit").value("piece"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createProduct_missingRequiredFields_returns400() throws Exception {
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "name": "Widget" }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_negativePriceRate_returns400() throws Exception {
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Widget",
                          "reference": "REF-001",
                          "unitPrice": -10.00,
                          "vatRate": 20.00,
                          "unit": "piece"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_vatRateAbove100_returns400() throws Exception {
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Widget",
                          "reference": "REF-001",
                          "unitPrice": 99.99,
                          "vatRate": 101.00,
                          "unit": "piece"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProduct_duplicateReference_returns409() throws Exception {
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Widget",
                          "reference": "REF-001",
                          "unitPrice": 99.99,
                          "vatRate": 20.00,
                          "unit": "piece"
                        }
                        """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Widget v2",
                          "reference": "REF-001",
                          "unitPrice": 49.99,
                          "vatRate": 20.00,
                          "unit": "piece"
                        }
                        """))
                .andExpect(status().isConflict());
    }

    // --- GET /api/products/{id} ---

    @Test
    void getProduct_existingProduct_returns200() throws Exception {
        String response = mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Widget",
                          "reference": "REF-001",
                          "unitPrice": 99.99,
                          "vatRate": 20.00,
                          "unit": "piece"
                        }
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asText();

        mockMvc.perform(get("/api/products/" + id)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("REF-001"));
    }

    @Test
    void getProduct_nonExistentId_returns404() throws Exception {
        mockMvc.perform(get("/api/products/00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // --- PUT /api/products/{id} ---

    @Test
    void updateProduct_validRequest_updatesFields() throws Exception {
        String response = mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Widget",
                          "reference": "REF-001",
                          "unitPrice": 99.99,
                          "vatRate": 20.00,
                          "unit": "piece"
                        }
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asText();

        mockMvc.perform(put("/api/products/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "name": "Super Widget", "unitPrice": 149.99 }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Super Widget"))
                .andExpect(jsonPath("$.unitPrice").value(149.99))
                .andExpect(jsonPath("$.reference").value("REF-001"));
    }

    // --- DELETE /api/products/{id} ---

    @Test
    void deleteProduct_existingProduct_returns204AndSoftDeletes() throws Exception {
        String response = mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Widget",
                          "reference": "REF-001",
                          "unitPrice": 99.99,
                          "vatRate": 20.00,
                          "unit": "piece"
                        }
                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .readTree(response).get("id").asText();

        mockMvc.perform(delete("/api/products/" + id)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // soft deleted — should no longer appear in list
        mockMvc.perform(get("/api/products")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteProduct_nonExistentId_returns404() throws Exception {
        mockMvc.perform(delete("/api/products/00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
