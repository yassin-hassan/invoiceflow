package com.example.invoiceflow.creditnote;

import com.example.invoiceflow.invoice.InvoiceLine;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "credit_note_lines")
@Data
@NoArgsConstructor
public class CreditNoteLine {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_note_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CreditNote creditNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_line_id", nullable = false)
    private InvoiceLine invoiceLine;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
