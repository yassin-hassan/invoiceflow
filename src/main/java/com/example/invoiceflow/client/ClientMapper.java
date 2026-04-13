package com.example.invoiceflow.client;

import com.example.invoiceflow.client.dto.ClientResponse;
import com.example.invoiceflow.user.Address;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ClientMapper {

    ClientResponse toResponse(Client client);

    ClientResponse.AddressResponse toAddressResponse(Address address);
}
