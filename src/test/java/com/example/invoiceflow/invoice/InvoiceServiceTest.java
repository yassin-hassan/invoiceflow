package com.example.invoiceflow.invoice;

import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.exception.ResourceNotFoundException;
import com.example.invoiceflow.invoice.dto.CreateInvoiceRequest;
import com.example.invoiceflow.invoice.dto.InvoiceLineRequest;
import com.example.invoiceflow.invoice.dto.RecordPaymentRequest;
import com.example.invoiceflow.invoice.dto.UpdateInvoiceRequest;
import com.example.invoiceflow.invoice.dto.UpdateInvoiceStatusRequest;
import com.example.invoiceflow.product.ProductRepository;
import com.example.invoiceflow.quote.Quote;
import com.example.invoiceflow.quote.QuoteLine;
import com.example.invoiceflow.quote.QuoteRepository;
import com.example.invoiceflow.quote.QuoteStatus;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ProductRepository productRepository;
    @Mock private QuoteRepository quoteRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private UserService userService;

    @InjectMocks
    private InvoiceService invoiceService;

    private User user;
    private Client client;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");

        client = new Client();
        client.setId(UUID.randomUUID());
        client.setUser(user);
        client.setName("Acme Corp");

        invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setNumber("FACT-2026-001");
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setLines(new HashSet<>());
        invoice.setPayments(new HashSet<>());
    }

    private InvoiceLineRequest buildLineRequest() {
        InvoiceLineRequest line = new InvoiceLineRequest();
        line.setDescription("Web development");
        line.setQuantity(new BigDecimal("10"));
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setVatRate(new BigDecimal("20.00"));
        return line;
    }

    private CreateInvoiceRequest buildCreateRequest() {
        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setClientId(client.getId());
        request.setLines(List.of(buildLineRequest()));
        return request;
    }

    // --- getInvoices ---

    @Test
    void getInvoices_returnsInvoicesForUser() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(invoice));

        List<Invoice> result = invoiceService.getInvoices("user@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNumber()).isEqualTo("FACT-2026-001");
    }

    // --- getInvoice ---

    @Test
    void getInvoice_existingInvoice_returnsInvoice() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));

        Invoice result = invoiceService.getInvoice("user@example.com", invoice.getId());

        assertThat(result.getNumber()).isEqualTo("FACT-2026-001");
    }

    @Test
    void getInvoice_notFound_throwsResourceNotFoundException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getInvoice("user@example.com", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- createInvoice ---

    @Test
    void createInvoice_validRequest_savesWithGeneratedNumber() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(client.getId(), user)).thenReturn(Optional.of(client));
        when(invoiceRepository.countByUserAndYear(eq(user), anyInt())).thenReturn(0L);
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = invoiceService.createInvoice("user@example.com", buildCreateRequest());

        assertThat(result.getNumber()).matches("FACT-\\d{4}-001");
        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(result.getLines()).hasSize(1);
    }

    @Test
    void createInvoice_defaultDates_issueDateTodayDuePlus30() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(client.getId(), user)).thenReturn(Optional.of(client));
        when(invoiceRepository.countByUserAndYear(eq(user), anyInt())).thenReturn(0L);
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = invoiceService.createInvoice("user@example.com", buildCreateRequest());

        assertThat(result.getIssueDate()).isEqualTo(LocalDate.now());
        assertThat(result.getDueDate()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    void createInvoice_clientNotFound_throwsResourceNotFoundException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.createInvoice("user@example.com", buildCreateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- convertFromQuote ---

    @Test
    void convertFromQuote_acceptedQuote_createsInvoiceAndMarksConverted() {
        QuoteLine quoteLine = new QuoteLine();
        quoteLine.setDescription("Web dev");
        quoteLine.setQuantity(new BigDecimal("5"));
        quoteLine.setUnitPrice(new BigDecimal("100.00"));
        quoteLine.setVatRate(new BigDecimal("20.00"));

        Quote quote = new Quote();
        quote.setId(UUID.randomUUID());
        quote.setUser(user);
        quote.setClient(client);
        quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setLines(List.of(quoteLine));

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(quote.getId(), user)).thenReturn(Optional.of(quote));
        when(invoiceRepository.countByUserAndYear(eq(user), anyInt())).thenReturn(0L);
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(quoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = invoiceService.convertFromQuote("user@example.com", quote.getId());

        assertThat(result.getQuote()).isEqualTo(quote);
        assertThat(result.getLines()).hasSize(1);
        assertThat(result.getLines().iterator().next().getDescription()).isEqualTo("Web dev");
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.CONVERTED);
    }

    @Test
    void convertFromQuote_nonAcceptedQuote_throwsIllegalStateException() {
        Quote quote = new Quote();
        quote.setId(UUID.randomUUID());
        quote.setStatus(QuoteStatus.SENT);

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(quote.getId(), user)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> invoiceService.convertFromQuote("user@example.com", quote.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- updateInvoice ---

    @Test
    void updateInvoice_draftInvoice_updatesFields() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateInvoiceRequest request = new UpdateInvoiceRequest();
        request.setPaymentTerms("30 days net");
        request.setLines(List.of(buildLineRequest()));

        Invoice result = invoiceService.updateInvoice("user@example.com", invoice.getId(), request);

        assertThat(result.getPaymentTerms()).isEqualTo("30 days net");
    }

    @Test
    void updateInvoice_nonDraftInvoice_throwsIllegalStateException() {
        invoice.setStatus(InvoiceStatus.SENT);
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));

        UpdateInvoiceRequest request = new UpdateInvoiceRequest();
        request.setLines(List.of(buildLineRequest()));

        assertThatThrownBy(() -> invoiceService.updateInvoice("user@example.com", invoice.getId(), request))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- updateStatus ---

    @Test
    void updateStatus_draftToSent_succeeds() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateInvoiceStatusRequest request = new UpdateInvoiceStatusRequest();
        request.setStatus(InvoiceStatus.SENT);

        Invoice result = invoiceService.updateStatus("user@example.com", invoice.getId(), request);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.SENT);
    }

    @Test
    void updateStatus_invalidTransition_throwsIllegalStateException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));

        UpdateInvoiceStatusRequest request = new UpdateInvoiceStatusRequest();
        request.setStatus(InvoiceStatus.PAID); // DRAFT → PAID is invalid

        assertThatThrownBy(() -> invoiceService.updateStatus("user@example.com", invoice.getId(), request))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- deleteInvoice ---

    @Test
    void deleteInvoice_draftInvoice_deletesInvoice() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));

        invoiceService.deleteInvoice("user@example.com", invoice.getId());

        verify(invoiceRepository).delete(invoice);
    }

    @Test
    void deleteInvoice_nonDraftInvoice_throwsIllegalStateException() {
        invoice.setStatus(InvoiceStatus.SENT);
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> invoiceService.deleteInvoice("user@example.com", invoice.getId()))
                .isInstanceOf(IllegalStateException.class);

        verify(invoiceRepository, never()).delete(any());
    }

    // --- recordPayment ---

    @Test
    void recordPayment_fullPayment_setsStatusToPaid() {
        InvoiceLine line = new InvoiceLine();
        line.setQuantity(new BigDecimal("1"));
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setVatRate(new BigDecimal("20.00"));
        invoice.getLines().add(line);
        invoice.setStatus(InvoiceStatus.SENT);

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setAmount(new BigDecimal("120.00")); // 100 + 20% VAT
        request.setMethod("bank_transfer");
        request.setPaidAt(LocalDate.now());

        Invoice result = invoiceService.recordPayment("user@example.com", invoice.getId(), request);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(result.getPayments()).hasSize(1);
    }

    @Test
    void recordPayment_partialPayment_setsStatusToPartiallyPaid() {
        InvoiceLine line = new InvoiceLine();
        line.setQuantity(new BigDecimal("1"));
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setVatRate(new BigDecimal("20.00"));
        invoice.getLines().add(line);
        invoice.setStatus(InvoiceStatus.SENT);

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setAmount(new BigDecimal("60.00"));
        request.setMethod("cash");
        request.setPaidAt(LocalDate.now());

        Invoice result = invoiceService.recordPayment("user@example.com", invoice.getId(), request);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
    }

    @Test
    void recordPayment_exceedsBalance_throwsIllegalArgumentException() {
        InvoiceLine line = new InvoiceLine();
        line.setQuantity(new BigDecimal("1"));
        line.setUnitPrice(new BigDecimal("100.00"));
        line.setVatRate(new BigDecimal("20.00"));
        invoice.getLines().add(line);
        invoice.setStatus(InvoiceStatus.SENT);

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));

        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setAmount(new BigDecimal("999.00"));
        request.setMethod("bank_transfer");
        request.setPaidAt(LocalDate.now());

        assertThatThrownBy(() -> invoiceService.recordPayment("user@example.com", invoice.getId(), request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordPayment_onPaidInvoice_throwsIllegalStateException() {
        invoice.setStatus(InvoiceStatus.PAID);

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));

        RecordPaymentRequest request = new RecordPaymentRequest();
        request.setAmount(new BigDecimal("50.00"));
        request.setMethod("cash");
        request.setPaidAt(LocalDate.now());

        assertThatThrownBy(() -> invoiceService.recordPayment("user@example.com", invoice.getId(), request))
                .isInstanceOf(IllegalStateException.class);
    }
}
