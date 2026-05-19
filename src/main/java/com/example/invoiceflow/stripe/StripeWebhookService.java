package com.example.invoiceflow.stripe;

import com.example.invoiceflow.audit.AuditAction;
import com.example.invoiceflow.audit.AuditLogService;
import com.example.invoiceflow.exception.ResourceNotFoundException;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.InvoiceStatus;
import com.example.invoiceflow.invoice.Payment;
import com.example.invoiceflow.invoice.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final StripeService stripeService;
    private final AuditLogService auditLogService;

    @Transactional
    public void handleEvent(Event event) {
        if (!"checkout.session.completed".equals(event.getType())) {
            log.debug("Ignoring Stripe event type: {}", event.getType());
            return;
        }

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject obj = deserializer.getObject().orElse(null);
        if (obj == null) {
            try {
                obj = deserializer.deserializeUnsafe();
            } catch (Exception e) {
                log.warn("Could not deserialize Stripe event {} (api version mismatch): {}",
                        event.getId(), e.getMessage());
                return;
            }
        }
        if (!(obj instanceof Session session)) {
            log.warn("checkout.session.completed event without Session payload: {}", event.getId());
            return;
        }

        String paymentIntentId = session.getPaymentIntent();
        if (paymentIntentId == null) {
            log.warn("Stripe session {} missing payment_intent; skipping", session.getId());
            return;
        }

        if (paymentRepository.findByStripePaymentIntentId(paymentIntentId).isPresent()) {
            log.info("Stripe payment_intent {} already recorded; skipping", paymentIntentId);
            return;
        }

        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || metadata.get("invoiceId") == null) {
            log.warn("Stripe session {} missing invoiceId metadata; skipping", session.getId());
            return;
        }

        UUID invoiceId;
        try {
            invoiceId = UUID.fromString(metadata.get("invoiceId"));
        } catch (IllegalArgumentException e) {
            log.warn("Stripe session {} has invalid invoiceId metadata: {}", session.getId(), metadata.get("invoiceId"));
            return;
        }

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        Long amountTotal = session.getAmountTotal();
        if (amountTotal == null || amountTotal <= 0) {
            log.warn("Stripe session {} has non-positive amount_total; skipping", session.getId());
            return;
        }
        BigDecimal amount = BigDecimal.valueOf(amountTotal)
                .movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP);

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(amount);
        payment.setMethod("stripe");
        payment.setPaidAt(LocalDate.now());
        payment.setStripePaymentIntentId(paymentIntentId);
        payment.setStripeCheckoutSessionId(session.getId());
        invoice.getPayments().add(payment);

        recomputeStatus(invoice);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            invalidateStripePaymentLink(invoice);
        }
        invoiceRepository.save(invoice);
        auditLogService.recordAnonymous(AuditAction.STRIPE_PAYMENT_CONFIRMED, "Invoice", invoice.getId().toString(),
                Map.of(
                        "userId", invoice.getUser().getId().toString(),
                        "amount", amount.toPlainString(),
                        "paymentIntentId", paymentIntentId,
                        "status", invoice.getStatus().name()));
        log.info("Recorded Stripe payment {} for invoice {} (amount={}, status={})",
                paymentIntentId, invoice.getId(), amount, invoice.getStatus());
    }

    private void recomputeStatus(Invoice invoice) {
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) return;

        BigDecimal totalInclVat = invoice.getLines().stream()
                .map(this::lineTotalInclVat)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid = invoice.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPaid.compareTo(totalInclVat) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
        } else if (totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }
    }

    private void invalidateStripePaymentLink(Invoice invoice) {
        String linkId = invoice.getStripePaymentLinkId();
        if (linkId == null) return;
        try {
            stripeService.deactivatePaymentLink(linkId);
        } catch (StripeException | RuntimeException e) {
            log.warn("Failed to deactivate Stripe payment link {} for invoice {} after PAID: {}",
                    linkId, invoice.getId(), e.getMessage());
        }
        invoice.setStripePaymentLinkId(null);
        invoice.setStripePaymentLinkUrl(null);
        invoice.setStripePaymentLinkCreatedAt(null);
    }

    private BigDecimal lineTotalInclVat(InvoiceLine line) {
        BigDecimal excl = line.getQuantity().multiply(line.getUnitPrice());
        BigDecimal vat = excl.multiply(line.getVatRate())
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return excl.add(vat);
    }
}
