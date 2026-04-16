package com.example.invoiceflow.quote;

import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.exception.ResourceNotFoundException;
import com.example.invoiceflow.product.Product;
import com.example.invoiceflow.product.ProductRepository;
import com.example.invoiceflow.quote.dto.CreateQuoteRequest;
import com.example.invoiceflow.quote.dto.QuoteLineRequest;
import com.example.invoiceflow.quote.dto.UpdateQuoteRequest;
import com.example.invoiceflow.quote.dto.UpdateQuoteStatusRequest;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock private QuoteRepository quoteRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserService userService;

    @InjectMocks
    private QuoteService quoteService;

    private User user;
    private Client client;
    private Quote quote;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");

        client = new Client();
        client.setId(UUID.randomUUID());
        client.setUser(user);
        client.setName("Acme Corp");
        client.setEmail("acme@example.com");

        quote = new Quote();
        quote.setId(UUID.randomUUID());
        quote.setUser(user);
        quote.setClient(client);
        quote.setNumber("DEV-2026-001");
        quote.setStatus(QuoteStatus.DRAFT);
        quote.setIssueDate(LocalDate.now());
        quote.setExpiryDate(LocalDate.now().plusDays(30));
    }

    private QuoteLineRequest buildLineRequest() {
        QuoteLineRequest line = new QuoteLineRequest();
        line.setDescription("Web development");
        line.setQuantity(new BigDecimal("10"));
        line.setUnitPrice(new BigDecimal("85.00"));
        line.setVatRate(new BigDecimal("20.00"));
        return line;
    }

    private CreateQuoteRequest buildCreateRequest() {
        CreateQuoteRequest request = new CreateQuoteRequest();
        request.setClientId(client.getId());
        request.setLines(List.of(buildLineRequest()));
        return request;
    }

    // --- getQuotes ---

    @Test
    void getQuotes_returnsQuotesForUser() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(quote));

        List<Quote> result = quoteService.getQuotes("user@example.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNumber()).isEqualTo("DEV-2026-001");
    }

    // --- getQuote ---

    @Test
    void getQuote_existingQuote_returnsQuote() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(quote.getId(), user)).thenReturn(Optional.of(quote));

        Quote result = quoteService.getQuote("user@example.com", quote.getId());

        assertThat(result.getNumber()).isEqualTo("DEV-2026-001");
    }

    @Test
    void getQuote_notFound_throwsResourceNotFoundException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quoteService.getQuote("user@example.com", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- createQuote ---

    @Test
    void createQuote_validRequest_savesQuoteWithGeneratedNumber() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(client.getId(), user)).thenReturn(Optional.of(client));
        when(quoteRepository.countByUserAndYear(eq(user), anyInt())).thenReturn(0L);
        when(quoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Quote result = quoteService.createQuote("user@example.com", buildCreateRequest());

        assertThat(result.getNumber()).matches("DEV-\\d{4}-001");
        assertThat(result.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(result.getLines()).hasSize(1);
        verify(quoteRepository).save(any());
    }

    @Test
    void createQuote_defaultDates_issueDateTodayExpiryPlus30() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(client.getId(), user)).thenReturn(Optional.of(client));
        when(quoteRepository.countByUserAndYear(eq(user), anyInt())).thenReturn(0L);
        when(quoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Quote result = quoteService.createQuote("user@example.com", buildCreateRequest());

        assertThat(result.getIssueDate()).isEqualTo(LocalDate.now());
        assertThat(result.getExpiryDate()).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    void createQuote_withProductId_linksProduct() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setUser(user);

        QuoteLineRequest lineRequest = buildLineRequest();
        lineRequest.setProductId(product.getId());

        CreateQuoteRequest request = new CreateQuoteRequest();
        request.setClientId(client.getId());
        request.setLines(List.of(lineRequest));

        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(client.getId(), user)).thenReturn(Optional.of(client));
        when(productRepository.findByIdAndUser(product.getId(), user)).thenReturn(Optional.of(product));
        when(quoteRepository.countByUserAndYear(eq(user), anyInt())).thenReturn(0L);
        when(quoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Quote result = quoteService.createQuote("user@example.com", request);

        assertThat(result.getLines().get(0).getProduct()).isEqualTo(product);
    }

    @Test
    void createQuote_clientNotFound_throwsResourceNotFoundException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quoteService.createQuote("user@example.com", buildCreateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createQuote_sequentialNumbering() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(clientRepository.findByIdAndUser(client.getId(), user)).thenReturn(Optional.of(client));
        when(quoteRepository.countByUserAndYear(eq(user), anyInt())).thenReturn(4L);
        when(quoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Quote result = quoteService.createQuote("user@example.com", buildCreateRequest());

        assertThat(result.getNumber()).endsWith("-005");
    }

    // --- updateQuote ---

    @Test
    void updateQuote_draftQuote_updatesFields() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(quote.getId(), user)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateQuoteRequest request = new UpdateQuoteRequest();
        request.setNotes("Updated notes");
        request.setLines(List.of(buildLineRequest()));

        Quote result = quoteService.updateQuote("user@example.com", quote.getId(), request);

        assertThat(result.getNotes()).isEqualTo("Updated notes");
    }

    @Test
    void updateQuote_nonDraftQuote_throwsIllegalStateException() {
        quote.setStatus(QuoteStatus.SENT);
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(quote.getId(), user)).thenReturn(Optional.of(quote));

        UpdateQuoteRequest request = new UpdateQuoteRequest();
        request.setNotes("Updated notes");
        request.setLines(List.of(buildLineRequest()));

        assertThatThrownBy(() -> quoteService.updateQuote("user@example.com", quote.getId(), request))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- updateStatus ---

    @Test
    void updateStatus_draftToSent_succeeds() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(quote.getId(), user)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateQuoteStatusRequest request = new UpdateQuoteStatusRequest();
        request.setStatus(QuoteStatus.SENT);

        Quote result = quoteService.updateStatus("user@example.com", quote.getId(), request);

        assertThat(result.getStatus()).isEqualTo(QuoteStatus.SENT);
    }

    @Test
    void updateStatus_sentToAccepted_succeeds() {
        quote.setStatus(QuoteStatus.SENT);
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(quote.getId(), user)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateQuoteStatusRequest request = new UpdateQuoteStatusRequest();
        request.setStatus(QuoteStatus.ACCEPTED);

        Quote result = quoteService.updateStatus("user@example.com", quote.getId(), request);

        assertThat(result.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
    }

    @Test
    void updateStatus_sentToRejected_succeeds() {
        quote.setStatus(QuoteStatus.SENT);
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(quote.getId(), user)).thenReturn(Optional.of(quote));
        when(quoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateQuoteStatusRequest request = new UpdateQuoteStatusRequest();
        request.setStatus(QuoteStatus.REJECTED);

        Quote result = quoteService.updateStatus("user@example.com", quote.getId(), request);

        assertThat(result.getStatus()).isEqualTo(QuoteStatus.REJECTED);
    }

    @Test
    void updateStatus_invalidTransition_throwsIllegalStateException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(quote.getId(), user)).thenReturn(Optional.of(quote));

        UpdateQuoteStatusRequest request = new UpdateQuoteStatusRequest();
        request.setStatus(QuoteStatus.ACCEPTED); // DRAFT → ACCEPTED is invalid

        assertThatThrownBy(() -> quoteService.updateStatus("user@example.com", quote.getId(), request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void updateStatus_rejectedQuote_cannotTransition() {
        quote.setStatus(QuoteStatus.REJECTED);
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(quote.getId(), user)).thenReturn(Optional.of(quote));

        UpdateQuoteStatusRequest request = new UpdateQuoteStatusRequest();
        request.setStatus(QuoteStatus.DRAFT);

        assertThatThrownBy(() -> quoteService.updateStatus("user@example.com", quote.getId(), request))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- deleteQuote ---

    @Test
    void deleteQuote_draftQuote_deletesQuote() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(quote.getId(), user)).thenReturn(Optional.of(quote));

        quoteService.deleteQuote("user@example.com", quote.getId());

        verify(quoteRepository).delete(quote);
    }

    @Test
    void deleteQuote_nonDraftQuote_throwsIllegalStateException() {
        quote.setStatus(QuoteStatus.SENT);
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(quote.getId(), user)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> quoteService.deleteQuote("user@example.com", quote.getId()))
                .isInstanceOf(IllegalStateException.class);

        verify(quoteRepository, never()).delete(any());
    }

    @Test
    void deleteQuote_notFound_throwsResourceNotFoundException() {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(quoteRepository.findByIdAndUser(any(), eq(user))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quoteService.deleteQuote("user@example.com", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
