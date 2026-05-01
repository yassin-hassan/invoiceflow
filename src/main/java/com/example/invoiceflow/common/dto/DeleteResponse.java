package com.example.invoiceflow.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeleteResponse {

    private Mode mode;

    public enum Mode {
        DELETED,
        ARCHIVED
    }
}
