package com.example.invoiceflow.user;

import java.util.Optional;

public interface UserRepository {
    User create(User user);
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
}
