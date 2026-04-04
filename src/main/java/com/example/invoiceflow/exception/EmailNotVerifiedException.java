package com.example.invoiceflow.exception;

public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Email address not verified. Please check your inbox.");
    }
}
