package com.example.invoiceflow.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateClientRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotBlank
    @Email
    private String email;

    @Pattern(regexp = "^\\+?[0-9\\s\\-().]{7,20}$", message = "invalid phone number")
    private String phone;

    @Size(max = 50)
    private String vatNumber;

    private String notes;

    private AddressRequest billingAddress;

    @Data
    public static class AddressRequest {
        @Size(max = 255)
        private String street;

        @Size(max = 20)
        private String postalCode;

        @Size(max = 100)
        private String city;

        @Size(max = 100)
        private String country;
    }
}
