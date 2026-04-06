package com.example.invoiceflow.user;

import com.example.invoiceflow.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void toResponse_mapsAllScalarFields() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setCompanyName("Acme");
        user.setPhone("+33600000000");
        user.setVatNumber("FR12345678901");
        user.setLogoUrl("http://example.com/logo.png");
        user.setPreferredLanguage("EN");

        UserResponse response = mapper.toResponse(user);

        assertThat(response.getId()).isEqualTo(user.getId());
        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getCompanyName()).isEqualTo("Acme");
        assertThat(response.getPhone()).isEqualTo("+33600000000");
        assertThat(response.getVatNumber()).isEqualTo("FR12345678901");
        assertThat(response.getLogoUrl()).isEqualTo("http://example.com/logo.png");
        assertThat(response.getPreferredLanguage()).isEqualTo("EN");
    }

    @Test
    void toResponse_withBillingAddress_mapsNestedFields() {
        Address address = new Address();
        address.setId(UUID.randomUUID());
        address.setStreet("1 rue de la Paix");
        address.setPostalCode("75001");
        address.setCity("Paris");
        address.setCountry("France");

        User user = new User();
        user.setEmail("test@example.com");
        user.setBillingAddress(address);

        UserResponse response = mapper.toResponse(user);

        assertThat(response.getBillingAddress()).isNotNull();
        assertThat(response.getBillingAddress().getId()).isEqualTo(address.getId());
        assertThat(response.getBillingAddress().getStreet()).isEqualTo("1 rue de la Paix");
        assertThat(response.getBillingAddress().getPostalCode()).isEqualTo("75001");
        assertThat(response.getBillingAddress().getCity()).isEqualTo("Paris");
        assertThat(response.getBillingAddress().getCountry()).isEqualTo("France");
    }

    @Test
    void toResponse_withoutBillingAddress_addressIsNull() {
        User user = new User();
        user.setEmail("test@example.com");

        UserResponse response = mapper.toResponse(user);

        assertThat(response.getBillingAddress()).isNull();
    }
}
