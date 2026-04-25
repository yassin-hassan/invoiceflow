package com.example.invoiceflow.auth;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    @Value("${app.twilio.account-sid:}")
    private String accountSid;

    @Value("${app.twilio.auth-token:}")
    private String authToken;

    @Value("${app.twilio.from-number:}")
    private String fromNumber;

    @PostConstruct
    void init() {
        if (!accountSid.isBlank() && !authToken.isBlank()) {
            Twilio.init(accountSid, authToken);
        }
    }

    public void sendOtpSms(String toNumber, String code) {
        log.info("[DEV] OTP code for {}: {}", toNumber, code);
        if (accountSid.isBlank() || authToken.isBlank() || fromNumber.isBlank()) {
            return;
        }
        Thread.ofVirtual().start(() -> {
            try {
                Message.creator(
                        new PhoneNumber(toNumber),
                        new PhoneNumber(fromNumber),
                        "Your InvoiceFlow verification code is: " + code + ". It expires in 5 minutes."
                ).create();
            } catch (Exception e) {
                log.warn("[DEV] Twilio send failed: {}", e.getMessage());
            }
        });
    }
}
