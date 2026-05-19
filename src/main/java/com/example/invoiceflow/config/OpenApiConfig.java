package com.example.invoiceflow.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI invoiceflowOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("InvoiceFlow API")
                .description("""
                    API REST de l'application InvoiceFlow — gestion de clients, produits,
                    devis, factures, paiements et notes de crédit pour entrepreneurs
                    francophones. Toutes les ressources métier sont scoppées au compte
                    utilisateur authentifié (multi-tenance par `user_id`).
                    """)
                .version("v1")
                .contact(new Contact()
                    .name("InvoiceFlow — Yassin Hassan")
                    .email("ynhassan22@gmail.com"))
                .license(new License()
                    .name("Projet pédagogique — Bachelier en Informatique (ICC)")
                    .url("https://github.com/yassinhassan/invoiceflow")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Développement local"),
                new Server().url("https://api.invoiceflow.app").description("Production (à venir)")
            ))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT obtenu via `POST /api/auth/login` "
                        + "(ou `POST /api/auth/2fa/verify` si la 2FA est activée).")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
