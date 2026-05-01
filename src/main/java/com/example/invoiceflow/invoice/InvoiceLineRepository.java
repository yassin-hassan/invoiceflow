package com.example.invoiceflow.invoice;

import com.example.invoiceflow.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

    boolean existsByProduct(Product product);
}
