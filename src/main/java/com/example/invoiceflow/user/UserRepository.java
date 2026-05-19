package com.example.invoiceflow.user;

import com.example.invoiceflow.admin.dto.AdminUserListItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    boolean existsByRole(Role role);
    Optional<User> findByEmail(String email);

    @Query("""
            SELECT new com.example.invoiceflow.admin.dto.AdminUserListItem(
                u.id, u.email, u.firstName, u.lastName, u.companyName,
                u.role, u.isActive, u.isEmailVerified, u.is2faEnabled,
                u.createdAt, u.lastLoginAt,
                (SELECT COUNT(c) FROM Client c WHERE c.user = u AND c.isActive = true),
                (SELECT COUNT(i) FROM Invoice i WHERE i.user = u),
                COALESCE((SELECT SUM(p.amount) FROM Payment p WHERE p.invoice.user = u), 0)
            )
            FROM User u
            ORDER BY u.createdAt DESC
            """)
    List<AdminUserListItem> findAllForAdmin();
}
