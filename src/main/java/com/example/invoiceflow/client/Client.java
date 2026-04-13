package com.example.invoiceflow.client;

import com.example.invoiceflow.user.Address;
import com.example.invoiceflow.user.User;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Data
@NoArgsConstructor
public class Client {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "vat_number")
    private String vatNumber;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "billing_address_id")
    private Address billingAddress;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
