package com.example.invoiceflow.user;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "addresses")
@Data
@NoArgsConstructor
public class Address {

    @Id
    @UuidGenerator
    private UUID id;

    private String street;

    @Column(name = "postal_code")
    private String postalCode;

    private String city;

    private String country;
}
