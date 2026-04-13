package com.example.invoiceflow.client;

import com.example.invoiceflow.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    List<Client> findByUserAndIsActiveTrue(User user);

    Optional<Client> findByIdAndUser(UUID id, User user);

    boolean existsByUserAndEmailAndIsActiveTrue(User user, String email);

    boolean existsByIdNotAndUserAndEmailAndIsActiveTrue(UUID id, User user, String email);

    Optional<Client> findByUserAndEmail(User user, String email);
}
