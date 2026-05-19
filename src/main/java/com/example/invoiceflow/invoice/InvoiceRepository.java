package com.example.invoiceflow.invoice;

import com.example.invoiceflow.client.Client;
import com.example.invoiceflow.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    boolean existsByClient(Client client);


    @EntityGraph(attributePaths = {"client", "lines", "lines.product", "payments", "quote"})
    List<Invoice> findByUserOrderByCreatedAtDesc(User user);

    @EntityGraph(attributePaths = {"client", "lines", "lines.product", "payments", "quote"})
    Optional<Invoice> findByIdAndUser(UUID id, User user);

    @Query("""
            SELECT COALESCE(MAX(CAST(SUBSTRING(i.number, LENGTH(i.number) - 2) AS int)), 0)
            FROM Invoice i
            WHERE i.user = :user AND i.number LIKE :prefix
            """)
    int findMaxNumberSuffixByUserAndPrefix(@Param("user") User user, @Param("prefix") String prefix);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.user.id = :userId")
    long countByUserId(@Param("userId") UUID userId);
}
