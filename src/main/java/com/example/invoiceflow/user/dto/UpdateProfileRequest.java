package com.example.invoiceflow.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Size(max = 255)
    private String companyName;

    @Pattern(regexp = "^\\+?[0-9\\s\\-().]{7,20}$", message = "invalid phone number")
    private String phone;

    @Size(max = 50)
    private String vatNumber;

    @Pattern(regexp = "^(FR|EN)$", message = "language must be FR or EN")
    private String preferredLanguage;

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
