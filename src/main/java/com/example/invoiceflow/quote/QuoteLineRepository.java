package com.example.invoiceflow.quote;

import com.example.invoiceflow.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface QuoteLineRepository extends JpaRepository<QuoteLine, UUID> {

    boolean existsByProduct(Product product);
}
