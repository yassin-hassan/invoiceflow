package com.example.invoiceflow.stripe;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(String secretKey, String webhookSecret) {

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }
}
