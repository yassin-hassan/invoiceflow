package com.example.invoiceflow.creditnote;

import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "credit_notes")
@Data
@NoArgsConstructor
public class CreditNote {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_invoice_id", nullable = false)
    private Invoice originalInvoice;

    @Column(length = 20)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CreditNoteStatus status = CreditNoteStatus.DRAFT;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CreditNoteLine> lines = new HashSet<>();

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
