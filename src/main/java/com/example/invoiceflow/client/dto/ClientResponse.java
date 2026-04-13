package com.example.invoiceflow.client.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ClientResponse {

    private UUID id;
    private String name;
    private String email;
    private String phone;
    private String vatNumber;
    private String notes;
    private boolean isActive;
    private LocalDateTime createdAt;
    private AddressResponse billingAddress;

    @Data
    public static class AddressResponse {
        private String street;
        private String postalCode;
        private String city;
        private String country;
    }
}
