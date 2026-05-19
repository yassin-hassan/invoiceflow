package com.example.invoiceflow.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.invoice.user.id = :userId")
    BigDecimal sumAmountByUserId(@Param("userId") UUID userId);
}
