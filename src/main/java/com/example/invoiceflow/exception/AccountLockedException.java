package com.example.invoiceflow.exception;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException() {
        super("Account is temporarily locked due to too many failed login attempts. Please try again later.");
    }
}
