package com.example.invoiceflow.invoice;

import com.example.invoiceflow.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @EntityGraph(attributePaths = {"client", "lines", "lines.product", "payments", "quote"})
    List<Invoice> findByUserOrderByCreatedAtDesc(User user);

    @EntityGraph(attributePaths = {"client", "lines", "lines.product", "payments", "quote"})
    Optional<Invoice> findByIdAndUser(UUID id, User user);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.user = :user AND YEAR(i.issueDate) = :year")
    long countByUserAndYear(@Param("user") User user, @Param("year") int year);
}
