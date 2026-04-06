package com.example.invoiceflow.user;

import com.example.invoiceflow.user.dto.UserResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);

    UserResponse.AddressResponse toAddressResponse(Address address);
}
