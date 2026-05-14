package com.example.invoiceflow.stripe;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final StripeProperties properties;
    private final StripeWebhookService webhookService;

    @PostMapping
    public ResponseEntity<String> receive(@RequestBody String payload,
                                          @RequestHeader("Stripe-Signature") String signature) {
        if (properties.webhookSecret() == null || properties.webhookSecret().isBlank()) {
            log.error("Stripe webhook received but STRIPE_WEBHOOK_SECRET is not configured");
            return ResponseEntity.status(503).body("webhook not configured");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, properties.webhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(400).body("invalid signature");
        }

        try {
            webhookService.handleEvent(event);
        } catch (RuntimeException e) {
            log.error("Failed to handle Stripe event {}: {}", event.getId(), e.getMessage(), e);
            return ResponseEntity.status(500).body("processing error");
        }

        return ResponseEntity.ok("ok");
    }
}
