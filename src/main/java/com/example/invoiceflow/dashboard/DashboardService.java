package com.example.invoiceflow.dashboard;

import com.example.invoiceflow.client.ClientRepository;
import com.example.invoiceflow.dashboard.dto.DashboardCounts;
import com.example.invoiceflow.dashboard.dto.DashboardResponse;
import com.example.invoiceflow.dashboard.dto.DashboardTotals;
import com.example.invoiceflow.dashboard.dto.InvoiceSummary;
import com.example.invoiceflow.dashboard.dto.PaymentRow;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int RECENT_LIMIT = 5;
    private static final Set<InvoiceStatus> OUTSTANDING_STATUSES =
            EnumSet.of(InvoiceStatus.SENT, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);
    private static final Set<QuoteStatus> OPEN_QUOTE_STATUSES =
            EnumSet.of(QuoteStatus.DRAFT, QuoteStatus.SENT);

    private final InvoiceRepository invoiceRepository;
    private final QuoteRepository quoteRepository;
    private final ClientRepository clientRepository;
    private final UserService userService;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String email) {
        User user = userService.getByEmail(email);
        List<Invoice> invoices = invoiceRepository.findByUserOrderByCreatedAtDesc(user);
        List<Quote> quotes = quoteRepository.findByUserOrderByCreatedAtDesc(user);
        long clientCount = clientRepository.findByUserAndIsActiveTrue(user).size();

        DashboardResponse response = new DashboardResponse();
        response.setTotals(computeTotals(invoices));
        response.setCounts(computeCounts(invoices, quotes, clientCount));
        response.setRecentInvoices(invoices.stream()
                .limit(RECENT_LIMIT)
                .map(this::toSummary)
                .toList());
        response.setRecentPayments(invoices.stream()
                .flatMap(inv -> inv.getPayments().stream().map(p -> toPaymentRow(inv, p)))
                .sorted(Comparator.comparing(PaymentRow::getPaidAt).reversed())
                .limit(RECENT_LIMIT)
                .toList());
        response.setTopOverdue(invoices.stream()
                .filter(inv -> inv.getStatus() == InvoiceStatus.OVERDUE)
                .sorted(Comparator.comparing(Invoice::getDueDate))
                .limit(RECENT_LIMIT)
                .map(this::toSummary)
                .toList());
        return response;
    }

    private DashboardTotals computeTotals(List<Invoice> invoices) {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;

        for (Invoice inv : invoices) {
            if (inv.getStatus() == InvoiceStatus.CANCELLED || inv.getStatus() == InvoiceStatus.DRAFT) {
                continue;
            }
            BigDecimal amountPaid = sumPayments(inv);
            BigDecimal amountDue = totalInclVat(inv).subtract(amountPaid);

            revenue = revenue.add(amountPaid);
            if (OUTSTANDING_STATUSES.contains(inv.getStatus())) {
                outstanding = outstanding.add(amountDue);
            }
            if (inv.getStatus() == InvoiceStatus.OVERDUE) {
                overdue = overdue.add(amountDue);
            }
        }

        DashboardTotals totals = new DashboardTotals();
        totals.setRevenue(revenue.setScale(2, RoundingMode.HALF_UP));
        totals.setOutstanding(outstanding.setScale(2, RoundingMode.HALF_UP));
        totals.setOverdue(overdue.setScale(2, RoundingMode.HALF_UP));
        return totals;
    }

    private DashboardCounts computeCounts(List<Invoice> invoices, List<Quote> quotes, long clientCount) {
        DashboardCounts counts = new DashboardCounts();
        counts.setClients(clientCount);
        counts.setDraftInvoices(invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.DRAFT).count());
        counts.setSentInvoices(invoices.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.SENT || i.getStatus() == InvoiceStatus.PARTIALLY_PAID)
                .count());
        counts.setOverdueInvoices(invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.OVERDUE).count());
        counts.setPaidInvoices(invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.PAID).count());
        counts.setOpenQuotes(quotes.stream().filter(q -> OPEN_QUOTE_STATUSES.contains(q.getStatus())).count());
        return counts;
    }

    private InvoiceSummary toSummary(Invoice inv) {
        InvoiceSummary s = new InvoiceSummary();
        s.setId(inv.getId());
        s.setNumber(inv.getNumber());
        s.setClientName(inv.getClient().getName());
        s.setStatus(inv.getStatus());
        BigDecimal total = totalInclVat(inv).setScale(2, RoundingMode.HALF_UP);
        BigDecimal due = total.subtract(sumPayments(inv)).setScale(2, RoundingMode.HALF_UP);
        s.setTotalInclVat(total);
        s.setAmountDue(due);
        s.setIssueDate(inv.getIssueDate());
        s.setDueDate(inv.getDueDate());
        return s;
    }

    private PaymentRow toPaymentRow(Invoice inv, Payment p) {
        PaymentRow row = new PaymentRow();
        row.setId(p.getId());
        row.setInvoiceId(inv.getId());
        row.setInvoiceNumber(inv.getNumber());
        row.setClientName(inv.getClient().getName());
        row.setAmount(p.getAmount());
        row.setMethod(p.getMethod());
        row.setPaidAt(p.getPaidAt());
        return row;
    }

    private BigDecimal totalInclVat(Invoice inv) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;
        for (InvoiceLine line : inv.getLines()) {
            BigDecimal lineHt = line.getQuantity().multiply(line.getUnitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineVat = lineHt.multiply(line.getVatRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineHt);
            totalVat = totalVat.add(lineVat);
        }
        return subtotal.add(totalVat);
    }

    private BigDecimal sumPayments(Invoice inv) {
        return inv.getPayments().stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
