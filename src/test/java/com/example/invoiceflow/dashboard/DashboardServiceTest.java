package com.example.invoiceflow.dashboard;

import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.dashboard.dto.DashboardResponse;
import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.invoice.InvoiceLine;
import com.example.invoiceflow.invoice.InvoiceRepository;
import com.example.invoiceflow.invoice.InvoiceStatus;
import com.example.invoiceflow.invoice.Payment;
import com.example.invoiceflow.quote.Quote;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private QuoteRepository quoteRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private UserService userService;

    @InjectMocks
    private DashboardService dashboardService;

    private User user;
    private Client client;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");

        client = new Client();
        client.setId(UUID.randomUUID());
        client.setUser(user);
        client.setName("Acme Corp");
    }

    private Invoice invoice(String number, InvoiceStatus status, LocalDate due,
                            BigDecimal lineHt, BigDecimal vatRate, BigDecimal... payments) {
        Invoice inv = new Invoice();
        inv.setId(UUID.randomUUID());
        inv.setUser(user);
        inv.setClient(client);
        inv.setNumber(number);
        inv.setStatus(status);
        inv.setIssueDate(LocalDate.now());
        inv.setDueDate(due);
        inv.setLines(new HashSet<>());
        inv.setPayments(new HashSet<>());

        InvoiceLine line = new InvoiceLine();
        line.setInvoice(inv);
        line.setDescription("work");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(lineHt);
        line.setVatRate(vatRate);
        inv.getLines().add(line);

        int i = 0;
        for (BigDecimal amount : payments) {
            Payment p = new Payment();
            p.setId(UUID.randomUUID());
            p.setInvoice(inv);
            p.setAmount(amount);
            p.setMethod("Bank transfer");
            p.setPaidAt(LocalDate.now().minusDays(i++));
            inv.getPayments().add(p);
        }
        return inv;
    }

    private Quote quote(QuoteStatus status) {
        Quote q = new Quote();
        q.setId(UUID.randomUUID());
        q.setUser(user);
        q.setClient(client);
        q.setStatus(status);
        return q;
    }

    private void stubBase(List<Invoice> invoices, List<Quote> quotes, List<Client> clients) {
        when(userService.getByEmail("user@example.com")).thenReturn(user);
        when(invoiceRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(invoices);
        when(quoteRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(quotes);
        when(clientRepository.findByUserAndIsActiveTrue(user)).thenReturn(clients);
    }

    @Test
    void emptyAccount_returnsZeroTotalsAndCounts() {
        stubBase(List.of(), List.of(), List.of());

        DashboardResponse r = dashboardService.getDashboard("user@example.com");

        assertThat(r.getTotals().getRevenue()).isEqualByComparingTo("0.00");
        assertThat(r.getTotals().getOutstanding()).isEqualByComparingTo("0.00");
        assertThat(r.getTotals().getOverdue()).isEqualByComparingTo("0.00");
        assertThat(r.getCounts().getClients()).isZero();
        assertThat(r.getCounts().getDraftInvoices()).isZero();
        assertThat(r.getCounts().getOpenQuotes()).isZero();
        assertThat(r.getRecentInvoices()).isEmpty();
        assertThat(r.getRecentPayments()).isEmpty();
        assertThat(r.getTopOverdue()).isEmpty();
    }

    @Test
    void revenue_sumsAllPaymentsAcrossSentPartiallyPaidPaidAndOverdue() {
        // PAID: 1000 + 200 vat = 1200, fully paid
        Invoice paid = invoice("A", InvoiceStatus.PAID, LocalDate.now(),
                new BigDecimal("1000"), new BigDecimal("20"), new BigDecimal("1200"));
        // PARTIALLY_PAID: 1000 + 200 = 1200, 500 paid
        Invoice partial = invoice("B", InvoiceStatus.PARTIALLY_PAID, LocalDate.now(),
                new BigDecimal("1000"), new BigDecimal("20"), new BigDecimal("500"));
        // OVERDUE with a deposit of 300 already paid
        Invoice overdue = invoice("C", InvoiceStatus.OVERDUE, LocalDate.now().minusDays(10),
                new BigDecimal("1000"), new BigDecimal("20"), new BigDecimal("300"));
        // CANCELLED with a payment that should be ignored
        Invoice cancelled = invoice("D", InvoiceStatus.CANCELLED, LocalDate.now(),
                new BigDecimal("1000"), new BigDecimal("20"), new BigDecimal("999"));
        // DRAFT — should be ignored entirely
        Invoice draft = invoice("E", InvoiceStatus.DRAFT, LocalDate.now(),
                new BigDecimal("500"), new BigDecimal("20"));

        stubBase(List.of(paid, partial, overdue, cancelled, draft), List.of(), List.of());

        DashboardResponse r = dashboardService.getDashboard("user@example.com");

        // revenue = 1200 + 500 + 300 (cancelled and draft excluded)
        assertThat(r.getTotals().getRevenue()).isEqualByComparingTo("2000.00");
        // outstanding = partial (700) + overdue (900); paid (0) excluded
        assertThat(r.getTotals().getOutstanding()).isEqualByComparingTo("1600.00");
        // overdue = 900
        assertThat(r.getTotals().getOverdue()).isEqualByComparingTo("900.00");
    }

    @Test
    void counts_bucketInvoicesByStatusAndQuotesByOpenness() {
        Invoice d1 = invoice("D1", InvoiceStatus.DRAFT, LocalDate.now(), new BigDecimal("100"), BigDecimal.ZERO);
        Invoice d2 = invoice("D2", InvoiceStatus.DRAFT, LocalDate.now(), new BigDecimal("100"), BigDecimal.ZERO);
        Invoice s = invoice("S", InvoiceStatus.SENT, LocalDate.now().plusDays(10), new BigDecimal("100"), BigDecimal.ZERO);
        Invoice pp = invoice("PP", InvoiceStatus.PARTIALLY_PAID, LocalDate.now().plusDays(5), new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("10"));
        Invoice ov = invoice("OV", InvoiceStatus.OVERDUE, LocalDate.now().minusDays(5), new BigDecimal("100"), BigDecimal.ZERO);
        Invoice p = invoice("P", InvoiceStatus.PAID, LocalDate.now(), new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"));

        Quote open1 = quote(QuoteStatus.DRAFT);
        Quote open2 = quote(QuoteStatus.SENT);
        Quote closed = quote(QuoteStatus.ACCEPTED);
        Quote converted = quote(QuoteStatus.CONVERTED);

        stubBase(List.of(d1, d2, s, pp, ov, p), List.of(open1, open2, closed, converted), List.of(client, client));

        DashboardResponse r = dashboardService.getDashboard("user@example.com");

        assertThat(r.getCounts().getDraftInvoices()).isEqualTo(2);
        // SENT + PARTIALLY_PAID
        assertThat(r.getCounts().getSentInvoices()).isEqualTo(2);
        assertThat(r.getCounts().getOverdueInvoices()).isEqualTo(1);
        assertThat(r.getCounts().getPaidInvoices()).isEqualTo(1);
        assertThat(r.getCounts().getOpenQuotes()).isEqualTo(2);
        assertThat(r.getCounts().getClients()).isEqualTo(2);
    }

    @Test
    void recentInvoices_cappedAtFiveAndPreservesRepositoryOrder() {
        // Repository returns by createdAt desc; service should preserve and truncate.
        Invoice i1 = invoice("FACT-001", InvoiceStatus.SENT, LocalDate.now().plusDays(10), new BigDecimal("100"), BigDecimal.ZERO);
        Invoice i2 = invoice("FACT-002", InvoiceStatus.SENT, LocalDate.now().plusDays(10), new BigDecimal("100"), BigDecimal.ZERO);
        Invoice i3 = invoice("FACT-003", InvoiceStatus.SENT, LocalDate.now().plusDays(10), new BigDecimal("100"), BigDecimal.ZERO);
        Invoice i4 = invoice("FACT-004", InvoiceStatus.SENT, LocalDate.now().plusDays(10), new BigDecimal("100"), BigDecimal.ZERO);
        Invoice i5 = invoice("FACT-005", InvoiceStatus.SENT, LocalDate.now().plusDays(10), new BigDecimal("100"), BigDecimal.ZERO);
        Invoice i6 = invoice("FACT-006", InvoiceStatus.SENT, LocalDate.now().plusDays(10), new BigDecimal("100"), BigDecimal.ZERO);

        stubBase(List.of(i1, i2, i3, i4, i5, i6), List.of(), List.of());

        DashboardResponse r = dashboardService.getDashboard("user@example.com");

        assertThat(r.getRecentInvoices()).hasSize(5);
        assertThat(r.getRecentInvoices().get(0).getNumber()).isEqualTo("FACT-001");
        assertThat(r.getRecentInvoices().get(4).getNumber()).isEqualTo("FACT-005");
    }

    @Test
    void recentPayments_sortedByPaidAtDescAndCappedAtFive() {
        // 7 payments across 2 invoices, dates oldest=0 to newest=6
        Invoice a = invoice("A", InvoiceStatus.PARTIALLY_PAID, LocalDate.now(),
                new BigDecimal("10000"), BigDecimal.ZERO,
                new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30"));
        Invoice b = invoice("B", InvoiceStatus.PARTIALLY_PAID, LocalDate.now(),
                new BigDecimal("10000"), BigDecimal.ZERO,
                new BigDecimal("40"), new BigDecimal("50"), new BigDecimal("60"), new BigDecimal("70"));

        stubBase(List.of(a, b), List.of(), List.of());

        DashboardResponse r = dashboardService.getDashboard("user@example.com");

        assertThat(r.getRecentPayments()).hasSize(5);
        // Most recent payment first — should be the one with paidAt = today (offset 0)
        assertThat(r.getRecentPayments().get(0).getPaidAt())
                .isAfterOrEqualTo(r.getRecentPayments().get(4).getPaidAt());
    }

    @Test
    void topOverdue_onlyOverdueInvoicesSortedByDueDateAscending() {
        Invoice old = invoice("OLD", InvoiceStatus.OVERDUE, LocalDate.now().minusDays(60),
                new BigDecimal("100"), BigDecimal.ZERO);
        Invoice newer = invoice("NEW", InvoiceStatus.OVERDUE, LocalDate.now().minusDays(5),
                new BigDecimal("100"), BigDecimal.ZERO);
        Invoice sent = invoice("SENT", InvoiceStatus.SENT, LocalDate.now().minusDays(40),
                new BigDecimal("100"), BigDecimal.ZERO);

        stubBase(List.of(newer, sent, old), List.of(), List.of());

        DashboardResponse r = dashboardService.getDashboard("user@example.com");

        assertThat(r.getTopOverdue()).hasSize(2);
        assertThat(r.getTopOverdue().get(0).getNumber()).isEqualTo("OLD");
        assertThat(r.getTopOverdue().get(1).getNumber()).isEqualTo("NEW");
    }

    @Test
    void summary_carriesTotalAndAmountDue() {
        Invoice inv = invoice("X", InvoiceStatus.PARTIALLY_PAID, LocalDate.now(),
                new BigDecimal("1000"), new BigDecimal("20"), new BigDecimal("500"));

        stubBase(List.of(inv), List.of(), List.of());

        DashboardResponse r = dashboardService.getDashboard("user@example.com");

        assertThat(r.getRecentInvoices()).hasSize(1);
        assertThat(r.getRecentInvoices().get(0).getTotalInclVat()).isEqualByComparingTo("1200.00");
        assertThat(r.getRecentInvoices().get(0).getAmountDue()).isEqualByComparingTo("700.00");
        assertThat(r.getRecentInvoices().get(0).getClientName()).isEqualTo("Acme Corp");
    }
}
