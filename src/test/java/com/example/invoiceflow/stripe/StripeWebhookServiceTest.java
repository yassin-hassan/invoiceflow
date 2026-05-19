package com.example.invoiceflow.stripe;

import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.InvoiceStatus;
import com.example.invoiceflow.invoice.Payment;
import com.example.invoiceflow.invoice.PaymentRepository;
import com.example.invoiceflow.user.User;
import com.stripe.exception.ApiException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private StripeService stripeService;
    @Mock private com.example.invoiceflow.audit.AuditLogService auditLogService;

    @InjectMocks
    private StripeWebhookService service;

    private User user;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());

        Client client = new Client();
        client.setId(UUID.randomUUID());
        client.setUser(user);
        client.setName("Acme");
        client.setEmail("acme@example.com");

        invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setNumber("FACT-2026-001");
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setLines(new HashSet<>());
        invoice.setPayments(new HashSet<>());

        InvoiceLine line = new InvoiceLine();
        line.setInvoice(invoice);
        line.setQuantity(new BigDecimal("1"));
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setVatRate(new BigDecimal("20.00"));
        line.setSortOrder(0);
        invoice.getLines().add(line);
    }

    private Event eventWithSession(String type, Session session) {
        Event event = mock(Event.class);
        lenient().when(event.getId()).thenReturn("evt_test_" + UUID.randomUUID());
        lenient().when(event.getType()).thenReturn(type);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        lenient().when(deserializer.getObject()).thenReturn(Optional.of(session));
        lenient().when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    private Session session(String paymentIntentId, Long amountTotalCents, Map<String, String> metadata) {
        Session session = new Session();
        session.setId("cs_test_" + UUID.randomUUID());
        session.setPaymentIntent(paymentIntentId);
        session.setAmountTotal(amountTotalCents);
        session.setMetadata(metadata);
        return session;
    }

    @Test
    void handleEvent_checkoutCompleted_fullAmount_marksPaidAndPersistsPayment() {
        Session session = session("pi_test_1", 12000L,
                Map.of("invoiceId", invoice.getId().toString(), "userId", user.getId().toString()));
        Event event = eventWithSession("checkout.session.completed", session);

        when(paymentRepository.findByStripePaymentIntentId("pi_test_1")).thenReturn(Optional.empty());
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleEvent(event);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getPayments()).hasSize(1);
        Payment payment = invoice.getPayments().iterator().next();
        assertThat(payment.getAmount()).isEqualByComparingTo("120.00");
        assertThat(payment.getMethod()).isEqualTo("stripe");
        assertThat(payment.getStripePaymentIntentId()).isEqualTo("pi_test_1");
        assertThat(payment.getStripeCheckoutSessionId()).isEqualTo(session.getId());
    }

    @Test
    void handleEvent_checkoutCompleted_partialAmount_setsPartiallyPaid() {
        Session session = session("pi_test_2", 6000L,
                Map.of("invoiceId", invoice.getId().toString()));
        Event event = eventWithSession("checkout.session.completed", session);

        when(paymentRepository.findByStripePaymentIntentId("pi_test_2")).thenReturn(Optional.empty());
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleEvent(event);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(invoice.getPayments()).hasSize(1);
    }

    @Test
    void handleEvent_duplicatePaymentIntent_skipsIdempotently() {
        Session session = session("pi_test_dupe", 12000L,
                Map.of("invoiceId", invoice.getId().toString()));
        Event event = eventWithSession("checkout.session.completed", session);

        Payment existing = new Payment();
        existing.setStripePaymentIntentId("pi_test_dupe");
        when(paymentRepository.findByStripePaymentIntentId("pi_test_dupe"))
                .thenReturn(Optional.of(existing));

        service.handleEvent(event);

        verify(invoiceRepository, never()).findById(any());
        verify(invoiceRepository, never()).save(any());
        assertThat(invoice.getPayments()).isEmpty();
    }

    @Test
    void handleEvent_ignoresNonCheckoutSessionTypes() {
        Session session = session("pi_test_x", 12000L, Map.of());
        Event event = eventWithSession("payment_intent.created", session);

        service.handleEvent(event);

        verify(paymentRepository, never()).findByStripePaymentIntentId(any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void handleEvent_missingInvoiceIdMetadata_skipsSilently() {
        Session session = session("pi_test_3", 12000L, Map.of());
        Event event = eventWithSession("checkout.session.completed", session);

        when(paymentRepository.findByStripePaymentIntentId("pi_test_3")).thenReturn(Optional.empty());

        service.handleEvent(event);

        verify(invoiceRepository, never()).findById(any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void handleEvent_invalidInvoiceIdMetadata_skipsSilently() {
        Session session = session("pi_test_4", 12000L, Map.of("invoiceId", "not-a-uuid"));
        Event event = eventWithSession("checkout.session.completed", session);

        when(paymentRepository.findByStripePaymentIntentId("pi_test_4")).thenReturn(Optional.empty());

        service.handleEvent(event);

        verify(invoiceRepository, never()).findById(any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void handleEvent_fullPayment_deactivatesStripePaymentLinkAndClearsFields() throws Exception {
        invoice.setStripePaymentLinkId("plink_abc");
        invoice.setStripePaymentLinkUrl("https://buy.stripe.com/test_abc");
        invoice.setStripePaymentLinkCreatedAt(java.time.LocalDateTime.now());

        Session session = session("pi_test_pl", 12000L,
                Map.of("invoiceId", invoice.getId().toString()));
        Event event = eventWithSession("checkout.session.completed", session);

        when(paymentRepository.findByStripePaymentIntentId("pi_test_pl")).thenReturn(Optional.empty());
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleEvent(event);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        verify(stripeService).deactivatePaymentLink("plink_abc");
        assertThat(invoice.getStripePaymentLinkId()).isNull();
        assertThat(invoice.getStripePaymentLinkUrl()).isNull();
        assertThat(invoice.getStripePaymentLinkCreatedAt()).isNull();
    }

    @Test
    void handleEvent_fullPayment_stripeDeactivateFailure_stillPersistsPaid() throws Exception {
        invoice.setStripePaymentLinkId("plink_xyz");
        invoice.setStripePaymentLinkUrl("https://buy.stripe.com/test_xyz");

        Session session = session("pi_test_plfail", 12000L,
                Map.of("invoiceId", invoice.getId().toString()));
        Event event = eventWithSession("checkout.session.completed", session);

        when(paymentRepository.findByStripePaymentIntentId("pi_test_plfail")).thenReturn(Optional.empty());
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new ApiException("stripe down", null, null, 500, null))
                .when(stripeService).deactivatePaymentLink("plink_xyz");

        service.handleEvent(event);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getStripePaymentLinkId()).isNull();
        assertThat(invoice.getStripePaymentLinkUrl()).isNull();
    }

    @Test
    void handleEvent_partialPayment_doesNotDeactivatePaymentLink() {
        invoice.setStripePaymentLinkId("plink_partial");
        invoice.setStripePaymentLinkUrl("https://buy.stripe.com/test_partial");

        Session session = session("pi_test_partial_pl", 6000L,
                Map.of("invoiceId", invoice.getId().toString()));
        Event event = eventWithSession("checkout.session.completed", session);

        when(paymentRepository.findByStripePaymentIntentId("pi_test_partial_pl")).thenReturn(Optional.empty());
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleEvent(event);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        verifyNoInteractions(stripeService);
        assertThat(invoice.getStripePaymentLinkId()).isEqualTo("plink_partial");
    }

    @Test
    void handleEvent_cancelledInvoice_recordsPaymentButPreservesStatus() {
        invoice.setStatus(InvoiceStatus.CANCELLED);
        Session session = session("pi_test_5", 12000L,
                Map.of("invoiceId", invoice.getId().toString()));
        Event event = eventWithSession("checkout.session.completed", session);

        when(paymentRepository.findByStripePaymentIntentId("pi_test_5")).thenReturn(Optional.empty());
        when(invoiceRepository.findById(invoice.getId())).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleEvent(event);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(invoice.getPayments()).hasSize(1);
    }
}
