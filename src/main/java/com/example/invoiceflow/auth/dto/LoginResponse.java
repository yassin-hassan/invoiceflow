package com.example.invoiceflow.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginResponse {
    private String token;
    private Boolean requires2fa;

    public static LoginResponse withToken(String token) {
        return LoginResponse.builder().token(token).build();
    }

    public static LoginResponse requires2fa() {
        return LoginResponse.builder().requires2fa(true).build();
    }
}
