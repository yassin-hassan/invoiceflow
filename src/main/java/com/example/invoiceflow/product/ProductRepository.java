package com.example.invoiceflow.product;

import com.example.invoiceflow.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByUserAndIsActiveTrue(User user);

    Optional<Product> findByIdAndUser(UUID id, User user);

    boolean existsByUserAndReference(User user, String reference);

    boolean existsByIdNotAndUserAndReference(UUID id, User user, String reference);
}
