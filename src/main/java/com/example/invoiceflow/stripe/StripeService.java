package com.example.invoiceflow.stripe;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentLink;
import com.stripe.model.Price;
import com.stripe.param.PaymentLinkCreateParams;
import com.stripe.param.PaymentLinkUpdateParams;
import com.stripe.param.PriceCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final StripeProperties properties;

    @PostConstruct
    void init() {
        if (properties.isConfigured()) {
            Stripe.apiKey = properties.secretKey();
        } else {
            log.warn("Stripe is not configured (STRIPE_SECRET_KEY missing) — payment links will be unavailable");
        }
    }

    public PaymentLink createPaymentLink(BigDecimal amountInclVat,
                                         String description,
                                         Map<String, String> metadata) throws StripeException {
        long amountCents = amountInclVat.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2).longValueExact();

        Price price = Price.create(PriceCreateParams.builder()
                .setCurrency("eur")
                .setUnitAmount(amountCents)
                .setProductData(PriceCreateParams.ProductData.builder()
                        .setName(description)
                        .build())
                .build());

        PaymentLinkCreateParams params = PaymentLinkCreateParams.builder()
                .addLineItem(PaymentLinkCreateParams.LineItem.builder()
                        .setPrice(price.getId())
                        .setQuantity(1L)
                        .build())
                .addPaymentMethodType(PaymentLinkCreateParams.PaymentMethodType.BANCONTACT)
                .addPaymentMethodType(PaymentLinkCreateParams.PaymentMethodType.SEPA_DEBIT)
                .addPaymentMethodType(PaymentLinkCreateParams.PaymentMethodType.CARD)
                .putAllMetadata(metadata)
                .build();

        return PaymentLink.create(params);
    }

    public void deactivatePaymentLink(String paymentLinkId) throws StripeException {
        PaymentLink link = PaymentLink.retrieve(paymentLinkId);
        link.update(PaymentLinkUpdateParams.builder().setActive(false).build());
    }
}
