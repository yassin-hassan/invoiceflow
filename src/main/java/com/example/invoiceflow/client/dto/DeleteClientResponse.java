package com.example.invoiceflow.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeleteClientResponse {

    private Mode mode;

    public enum Mode {
        DELETED,
        ARCHIVED
    }
}
