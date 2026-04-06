package com.example.invoiceflow.user.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UserResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String companyName;
    private String phone;
    private String vatNumber;
    private String logoUrl;
    private String preferredLanguage;
    private boolean is2faEnabled;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private AddressResponse billingAddress;

    @Data
    public static class AddressResponse {
        private UUID id;
        private String street;
        private String postalCode;
        private String city;
        private String country;
    }
}
