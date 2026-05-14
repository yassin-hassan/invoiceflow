package com.example.invoiceflow.creditnote;

import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.creditnote.dto.CreateCreditNoteRequest;
import com.example.invoiceflow.creditnote.dto.CreditNoteLineRequest;
import com.example.invoiceflow.creditnote.dto.UpdateCreditNoteRequest;
import com.example.invoiceflow.exception.ResourceNotFoundException;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.invoice.InvoiceLineRepository;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.InvoiceStatus;
import com.example.invoiceflow.stripe.StripeService;
import com.example.invoiceflow.user.User;
import com.example.invoiceflow.user.UserService;
import com.stripe.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditNoteServiceTest {

    @Mock private CreditNoteRepository creditNoteRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceLineRepository invoiceLineRepository;
    @Mock private UserService userService;
    @Mock private StripeService stripeService;

    @InjectMocks
    private CreditNoteService creditNoteService;

    private User user;
    private Client client;
    private Invoice invoice;
    private InvoiceLine invoiceLine;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");

        client = new Client();
        client.setId(UUID.randomUUID());
        client.setUser(user);
        client.setName("Acme Corp");

        invoiceLine = new InvoiceLine();
        invoiceLine.setId(UUID.randomUUID());
        invoiceLine.setDescription("Web development");
        invoiceLine.setQuantity(new BigDecimal("10.00"));
        invoiceLine.setUnitPrice(new BigDecimal("100.00"));
        invoiceLine.setVatRate(new BigDecimal("20.00"));

        invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setUser(user);
        invoice.setClient(client);
        invoice.setNumber("FACT-2026-001");
        invoice.setStatus(InvoiceStatus.SENT);
        invoice.setIssueDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setLines(new HashSet<>());
        invoice.getLines().add(invoiceLine);
        invoiceLine.setInvoice(invoice);
    }

    private CreditNoteLineRequest buildLineRequest(UUID invoiceLineId, BigDecimal quantity) {
        CreditNoteLineRequest line = new CreditNoteLineRequest();
        line.setInvoiceLineId(invoiceLineId);
        line.setQuantity(quantity);
        return line;
    }

    private CreateCreditNoteRequest buildCreateRequest() {
        CreateCreditNoteRequest request = new CreateCreditNoteRequest();
        request.setReason("Wrong quantity invoiced");
        request.setIssueDate(LocalDate.now());
        request.setLines(List.of(buildLineRequest(invoiceLine.getId(), new BigDecimal("2.00"))));
        return request;
    }

    // --- create ---

    @Test
    void create_validRequest_savesAsDraftWithNullNumber() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));
        when(creditNoteRepository.findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(invoice, CreditNoteStatus.ISSUED))
                .thenReturn(List.of());
        when(creditNoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditNote result = creditNoteService.create("user@example.com", invoice.getId(), buildCreateRequest());

        assertThat(result.getNumber()).isNull();
        assertThat(result.getStatus()).isEqualTo(CreditNoteStatus.DRAFT);
        assertThat(result.getOriginalInvoice()).isEqualTo(invoice);
        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getReason()).isEqualTo("Wrong quantity invoiced");
        assertThat(result.getLines()).hasSize(1);
        assertThat(result.getLines().iterator().next().getQuantity()).isEqualByComparingTo("2.00");
    }

    @Test
    void create_defaultsIssueDateToToday() {
        CreateCreditNoteRequest request = buildCreateRequest();
        request.setIssueDate(null);

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));
        when(creditNoteRepository.findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(invoice, CreditNoteStatus.ISSUED))
                .thenReturn(List.of());
        when(creditNoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditNote result = creditNoteService.create("user@example.com", invoice.getId(), request);

        assertThat(result.getIssueDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void create_invoiceNotOwned_throwsNotFound() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                creditNoteService.create("user@example.com", UUID.randomUUID(), buildCreateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(creditNoteRepository, never()).save(any());
    }

    @Test
    void create_invoiceInDraftStatus_throwsIllegalState() {
        invoice.setStatus(InvoiceStatus.DRAFT);
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() ->
                creditNoteService.create("user@example.com", invoice.getId(), buildCreateRequest()))
                .isInstanceOf(IllegalStateException.class);

        verify(creditNoteRepository, never()).save(any());
    }

    @Test
    void create_invoiceInCancelledStatus_throwsIllegalState() {
        invoice.setStatus(InvoiceStatus.CANCELLED);
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() ->
                creditNoteService.create("user@example.com", invoice.getId(), buildCreateRequest()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void create_lineQuantityExceedsOriginal_throwsIllegalState() {
        CreateCreditNoteRequest request = buildCreateRequest();
        request.getLines().get(0).setQuantity(new BigDecimal("99.00"));

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));
        when(creditNoteRepository.findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(invoice, CreditNoteStatus.ISSUED))
                .thenReturn(List.of());

        assertThatThrownBy(() ->
                creditNoteService.create("user@example.com", invoice.getId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds original quantity");

        verify(creditNoteRepository, never()).save(any());
    }

    @Test
    void create_lineRefersToOtherInvoicesLine_throwsIllegalState() {
        CreateCreditNoteRequest request = buildCreateRequest();
        request.getLines().get(0).setInvoiceLineId(UUID.randomUUID());

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() ->
                creditNoteService.create("user@example.com", invoice.getId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong to invoice");

        verify(creditNoteRepository, never()).save(any());
    }

    @Test
    void create_secondCreditNote_withinRemainingHeadroom_succeeds() {
        CreditNote alreadyIssued = new CreditNote();
        alreadyIssued.setId(UUID.randomUUID());
        alreadyIssued.setUser(user);
        alreadyIssued.setOriginalInvoice(invoice);
        alreadyIssued.setStatus(CreditNoteStatus.ISSUED);
        CreditNoteLine prior = new CreditNoteLine();
        prior.setCreditNote(alreadyIssued);
        prior.setInvoiceLine(invoiceLine);
        prior.setQuantity(new BigDecimal("4.00"));
        alreadyIssued.getLines().add(prior);

        CreateCreditNoteRequest request = buildCreateRequest();
        request.getLines().get(0).setQuantity(new BigDecimal("3.00"));

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));
        when(creditNoteRepository.findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(invoice, CreditNoteStatus.ISSUED))
                .thenReturn(List.of(alreadyIssued));
        when(creditNoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditNote result = creditNoteService.create("user@example.com", invoice.getId(), request);

        assertThat(result.getLines()).hasSize(1);
        assertThat(result.getLines().iterator().next().getQuantity()).isEqualByComparingTo("3.00");
    }

    @Test
    void create_secondCreditNote_exceedsPerLineCumulative_throwsIllegalState() {
        CreditNote alreadyIssued = new CreditNote();
        alreadyIssued.setId(UUID.randomUUID());
        alreadyIssued.setUser(user);
        alreadyIssued.setOriginalInvoice(invoice);
        alreadyIssued.setStatus(CreditNoteStatus.ISSUED);
        CreditNoteLine prior = new CreditNoteLine();
        prior.setCreditNote(alreadyIssued);
        prior.setInvoiceLine(invoiceLine);
        prior.setQuantity(new BigDecimal("8.00"));
        alreadyIssued.getLines().add(prior);

        CreateCreditNoteRequest request = buildCreateRequest();
        request.getLines().get(0).setQuantity(new BigDecimal("3.00"));

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByIdAndUser(invoice.getId(), user)).thenReturn(Optional.of(invoice));
        when(creditNoteRepository.findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(invoice, CreditNoteStatus.ISSUED))
                .thenReturn(List.of(alreadyIssued));

        assertThatThrownBy(() ->
                creditNoteService.create("user@example.com", invoice.getId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds original quantity");

        verify(creditNoteRepository, never()).save(any());
    }

    // --- update ---

    @Test
    void update_draft_replacesLines() {
        CreditNote existing = new CreditNote();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setOriginalInvoice(invoice);
        existing.setStatus(CreditNoteStatus.DRAFT);
        existing.setIssueDate(LocalDate.now());
        existing.setReason("old reason");
        CreditNoteLine oldLine = new CreditNoteLine();
        oldLine.setCreditNote(existing);
        oldLine.setInvoiceLine(invoiceLine);
        oldLine.setQuantity(new BigDecimal("1.00"));
        existing.getLines().add(oldLine);

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(creditNoteRepository.findByIdAndUser(existing.getId(), user))
                .thenReturn(Optional.of(existing));
        when(creditNoteRepository.findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(invoice, CreditNoteStatus.ISSUED))
                .thenReturn(List.of());
        when(creditNoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCreditNoteRequest request = new UpdateCreditNoteRequest();
        request.setReason("updated reason");
        request.setLines(List.of(buildLineRequest(invoiceLine.getId(), new BigDecimal("3.00"))));

        CreditNote result = creditNoteService.update("user@example.com", existing.getId(), request);

        assertThat(result.getReason()).isEqualTo("updated reason");
        assertThat(result.getLines()).hasSize(1);
        assertThat(result.getLines().iterator().next().getQuantity()).isEqualByComparingTo("3.00");
    }

    @Test
    void update_issued_throwsIllegalState() {
        CreditNote existing = new CreditNote();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setOriginalInvoice(invoice);
        existing.setStatus(CreditNoteStatus.ISSUED);

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(creditNoteRepository.findByIdAndUser(existing.getId(), user))
                .thenReturn(Optional.of(existing));

        UpdateCreditNoteRequest request = new UpdateCreditNoteRequest();
        request.setReason("updated reason");
        request.setLines(List.of(buildLineRequest(invoiceLine.getId(), new BigDecimal("1.00"))));

        assertThatThrownBy(() ->
                creditNoteService.update("user@example.com", existing.getId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");

        verify(creditNoteRepository, never()).save(any());
    }

    // --- delete ---

    @Test
    void delete_draft_deletes() {
        CreditNote existing = new CreditNote();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setOriginalInvoice(invoice);
        existing.setStatus(CreditNoteStatus.DRAFT);

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(creditNoteRepository.findByIdAndUser(existing.getId(), user))
                .thenReturn(Optional.of(existing));

        creditNoteService.delete("user@example.com", existing.getId());

        verify(creditNoteRepository).delete(existing);
    }

    @Test
    void delete_issued_throwsIllegalState() {
        CreditNote existing = new CreditNote();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setOriginalInvoice(invoice);
        existing.setStatus(CreditNoteStatus.ISSUED);

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(creditNoteRepository.findByIdAndUser(existing.getId(), user))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                creditNoteService.delete("user@example.com", existing.getId()))
                .isInstanceOf(IllegalStateException.class);

        verify(creditNoteRepository, never()).delete(any());
    }

    // --- issue ---

    @Test
    void issue_draft_assignsAvNumberAndIssuedAt() {
        CreditNote existing = new CreditNote();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setOriginalInvoice(invoice);
        existing.setStatus(CreditNoteStatus.DRAFT);
        existing.setIssueDate(LocalDate.of(2026, 5, 13));

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(creditNoteRepository.findByIdAndUser(existing.getId(), user))
                .thenReturn(Optional.of(existing));
        when(creditNoteRepository.findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(invoice, CreditNoteStatus.ISSUED))
                .thenReturn(List.of());
        when(creditNoteRepository.findMaxNumberSuffixByUserAndPrefix(eq(user), anyString()))
                .thenReturn(0);
        when(creditNoteRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditNote result = creditNoteService.issue("user@example.com", existing.getId());

        assertThat(result.getStatus()).isEqualTo(CreditNoteStatus.ISSUED);
        assertThat(result.getNumber()).isEqualTo("AV-2026-001");
        assertThat(result.getIssuedAt()).isNotNull();
    }

    @Test
    void issue_assignsSequentialPerYear() {
        CreditNote existing = new CreditNote();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setOriginalInvoice(invoice);
        existing.setStatus(CreditNoteStatus.DRAFT);
        existing.setIssueDate(LocalDate.of(2026, 5, 13));

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(creditNoteRepository.findByIdAndUser(existing.getId(), user))
                .thenReturn(Optional.of(existing));
        when(creditNoteRepository.findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(invoice, CreditNoteStatus.ISSUED))
                .thenReturn(List.of());
        ArgumentCaptor<String> prefixCaptor = ArgumentCaptor.forClass(String.class);
        when(creditNoteRepository.findMaxNumberSuffixByUserAndPrefix(eq(user), prefixCaptor.capture()))
                .thenReturn(7);
        when(creditNoteRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditNote result = creditNoteService.issue("user@example.com", existing.getId());

        assertThat(prefixCaptor.getValue()).isEqualTo("AV-2026-%");
        assertThat(result.getNumber()).isEqualTo("AV-2026-008");
    }

    @Test
    void issue_draftWouldExceedCumulative_throwsIllegalState() {
        CreditNote existing = new CreditNote();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setOriginalInvoice(invoice);
        existing.setStatus(CreditNoteStatus.DRAFT);
        existing.setIssueDate(LocalDate.of(2026, 5, 13));
        CreditNoteLine pending = new CreditNoteLine();
        pending.setCreditNote(existing);
        pending.setInvoiceLine(invoiceLine);
        pending.setQuantity(new BigDecimal("4.00"));
        existing.getLines().add(pending);

        CreditNote alreadyIssued = new CreditNote();
        alreadyIssued.setId(UUID.randomUUID());
        alreadyIssued.setUser(user);
        alreadyIssued.setOriginalInvoice(invoice);
        alreadyIssued.setStatus(CreditNoteStatus.ISSUED);
        CreditNoteLine prior = new CreditNoteLine();
        prior.setCreditNote(alreadyIssued);
        prior.setInvoiceLine(invoiceLine);
        prior.setQuantity(new BigDecimal("8.00"));
        alreadyIssued.getLines().add(prior);

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(creditNoteRepository.findByIdAndUser(existing.getId(), user))
                .thenReturn(Optional.of(existing));
        when(creditNoteRepository.findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(invoice, CreditNoteStatus.ISSUED))
                .thenReturn(List.of(alreadyIssued));

        assertThatThrownBy(() ->
                creditNoteService.issue("user@example.com", existing.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exceeds original quantity");

        verify(creditNoteRepository, never()).saveAndFlush(any());
    }

    @Test
    void issue_deactivatesStripePaymentLinkAndClearsFields() throws Exception {
        invoice.setStripePaymentLinkId("plink_abc");
        invoice.setStripePaymentLinkUrl("https://buy.stripe.com/test_abc");
        invoice.setStripePaymentLinkCreatedAt(java.time.LocalDateTime.now());

        CreditNote existing = new CreditNote();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setOriginalInvoice(invoice);
        existing.setStatus(CreditNoteStatus.DRAFT);
        existing.setIssueDate(LocalDate.of(2026, 5, 13));

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(creditNoteRepository.findByIdAndUser(existing.getId(), user))
                .thenReturn(Optional.of(existing));
        when(creditNoteRepository.findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(invoice, CreditNoteStatus.ISSUED))
                .thenReturn(List.of());
        when(creditNoteRepository.findMaxNumberSuffixByUserAndPrefix(eq(user), anyString()))
                .thenReturn(0);
        when(creditNoteRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        creditNoteService.issue("user@example.com", existing.getId());

        verify(stripeService).deactivatePaymentLink("plink_abc");
        verify(invoiceRepository).save(invoice);
        assertThat(invoice.getStripePaymentLinkId()).isNull();
        assertThat(invoice.getStripePaymentLinkUrl()).isNull();
        assertThat(invoice.getStripePaymentLinkCreatedAt()).isNull();
    }

    @Test
    void issue_stripeFailure_stillIssuesAndClearsFields() throws Exception {
        invoice.setStripePaymentLinkId("plink_xyz");
        invoice.setStripePaymentLinkUrl("https://buy.stripe.com/test_xyz");

        CreditNote existing = new CreditNote();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setOriginalInvoice(invoice);
        existing.setStatus(CreditNoteStatus.DRAFT);
        existing.setIssueDate(LocalDate.of(2026, 5, 13));

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(creditNoteRepository.findByIdAndUser(existing.getId(), user))
                .thenReturn(Optional.of(existing));
        when(creditNoteRepository.findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(invoice, CreditNoteStatus.ISSUED))
                .thenReturn(List.of());
        when(creditNoteRepository.findMaxNumberSuffixByUserAndPrefix(eq(user), anyString()))
                .thenReturn(0);
        when(creditNoteRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new ApiException("stripe down", null, null, 500, null))
                .when(stripeService).deactivatePaymentLink("plink_xyz");

        CreditNote result = creditNoteService.issue("user@example.com", existing.getId());

        assertThat(result.getStatus()).isEqualTo(CreditNoteStatus.ISSUED);
        assertThat(invoice.getStripePaymentLinkId()).isNull();
        assertThat(invoice.getStripePaymentLinkUrl()).isNull();
    }

    @Test
    void issue_invoiceWithoutStripeLink_skipsStripeCall() {
        CreditNote existing = new CreditNote();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setOriginalInvoice(invoice);
        existing.setStatus(CreditNoteStatus.DRAFT);
        existing.setIssueDate(LocalDate.of(2026, 5, 13));

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(creditNoteRepository.findByIdAndUser(existing.getId(), user))
                .thenReturn(Optional.of(existing));
        when(creditNoteRepository.findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(invoice, CreditNoteStatus.ISSUED))
                .thenReturn(List.of());
        when(creditNoteRepository.findMaxNumberSuffixByUserAndPrefix(eq(user), anyString()))
                .thenReturn(0);
        when(creditNoteRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        creditNoteService.issue("user@example.com", existing.getId());

        org.mockito.Mockito.verifyNoInteractions(stripeService);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void issue_alreadyIssued_throwsIllegalState() {
        CreditNote existing = new CreditNote();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setOriginalInvoice(invoice);
        existing.setStatus(CreditNoteStatus.ISSUED);

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(creditNoteRepository.findByIdAndUser(existing.getId(), user))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                creditNoteService.issue("user@example.com", existing.getId()))
                .isInstanceOf(IllegalStateException.class);

        verify(creditNoteRepository, never()).saveAndFlush(any());
    }

    @Test
    void get_notFound_throwsResourceNotFound() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(creditNoteRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                creditNoteService.get("user@example.com", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_returnsCreditNotesForUser() {
        CreditNote cn = new CreditNote();
        cn.setId(UUID.randomUUID());
        cn.setUser(user);
        cn.setOriginalInvoice(invoice);
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(creditNoteRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(cn));

        List<CreditNote> result = creditNoteService.list("user@example.com");

        assertThat(result).hasSize(1).first().isEqualTo(cn);
    }
}
