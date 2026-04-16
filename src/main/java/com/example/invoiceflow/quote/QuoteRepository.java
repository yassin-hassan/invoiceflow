package com.example.invoiceflow.quote;

import com.example.invoiceflow.user.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteRepository extends JpaRepository<Quote, UUID> {

    @EntityGraph(attributePaths = {"client", "lines", "lines.product"})
    List<Quote> findByUserOrderByCreatedAtDesc(User user);

    @EntityGraph(attributePaths = {"client", "lines", "lines.product"})
    Optional<Quote> findByIdAndUser(UUID id, User user);

    @Query("SELECT COUNT(q) FROM Quote q WHERE q.user = :user AND YEAR(q.issueDate) = :year")
    long countByUserAndYear(@Param("user") User user, @Param("year") int year);
}
