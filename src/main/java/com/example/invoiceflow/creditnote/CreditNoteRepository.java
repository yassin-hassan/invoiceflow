package com.example.invoiceflow.creditnote;

import com.example.invoiceflow.invoice.Invoice;
import com.example.invoiceflow.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {

    @EntityGraph(attributePaths = {
            "originalInvoice", "originalInvoice.client",
            "lines", "lines.invoiceLine"
    })
    List<CreditNote> findByUserOrderByCreatedAtDesc(User user);

    @EntityGraph(attributePaths = {
            "originalInvoice", "originalInvoice.client",
            "lines", "lines.invoiceLine"
    })
    Optional<CreditNote> findByIdAndUser(UUID id, User user);

    @EntityGraph(attributePaths = { "lines", "lines.invoiceLine" })
    List<CreditNote> findAllByOriginalInvoiceOrderByCreatedAtAsc(Invoice originalInvoice);

    @EntityGraph(attributePaths = { "lines", "lines.invoiceLine" })
    List<CreditNote> findAllByOriginalInvoiceAndStatusOrderByCreatedAtAsc(Invoice originalInvoice, CreditNoteStatus status);

    @Query("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(c.number, LENGTH(c.number) - 2) AS int)), 0)
            FROM CreditNote c
            WHERE c.user = :user AND c.number LIKE :prefix
            """)
    int findMaxNumberSuffixByUserAndPrefix(@Param("user") User user, @Param("prefix") String prefix);
}
