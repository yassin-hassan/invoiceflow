package com.example.invoiceflow.admin;

import com.example.invoiceflow.PostgresTestContainer;
import com.example.invoiceflow.security.JwtService;
import com.example.invoiceflow.user.Role;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AdminControllerIT extends PostgresTestContainer {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        userRepository.deleteAll();

        User user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash(passwordEncoder.encode("Password1"));
        user.setEmailVerified(true);
        user.setRole(Role.USER);
        userRepository.save(user);
        userToken = jwtService.generateToken("user@example.com", Role.USER);

        User admin = new User();
        admin.setEmail("admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("Password1"));
        admin.setEmailVerified(true);
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        adminToken = jwtService.generateToken("admin@example.com", Role.ADMIN);
    }

    @Test
    void ping_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ping_userRole_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void ping_adminRole_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
