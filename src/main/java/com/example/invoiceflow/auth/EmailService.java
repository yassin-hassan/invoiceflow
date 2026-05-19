package com.example.invoiceflow.auth;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final MessageSource messageSource;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.base-url}")
    private String baseUrl;

    public void sendVerificationEmail(String to, String token, Locale locale) {
        Locale l = nonNull(locale);
        String link = baseUrl + "/verify?token=" + token;
        String subject = messageSource.getMessage("email.verification.subject", null, l);
        String html = messageSource.getMessage("email.verification.body", new Object[]{link}, l);
        send(to, subject, html);
    }

    public void sendPasswordResetEmail(String to, String token, Locale locale) {
        Locale l = nonNull(locale);
        String link = baseUrl + "/api/auth/reset-password?token=" + token;
        String subject = messageSource.getMessage("email.reset.subject", null, l);
        String html = messageSource.getMessage("email.reset.body", new Object[]{link}, l);
        send(to, subject, html);
    }

    private void send(String to, String subject, String htmlBody) {
        Thread.ofVirtual().start(() -> {
            try {
                var message = mailSender.createMimeMessage();
                var helper = new MimeMessageHelper(message, "UTF-8");
                helper.setFrom(from);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
                mailSender.send(message);
            } catch (Exception e) {
                throw new RuntimeException("Failed to send email to " + to, e);
            }
        });
    }

    public void sendInvoice(String to, String subject, String htmlBody, String pdfFilename, byte[] pdfBytes) {
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.addAttachment(pdfFilename, new ByteArrayResource(pdfBytes));
            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
            throw new IllegalStateException("Failed to send invoice email to " + to, ex);
        }
    }

    private static Locale nonNull(Locale locale) {
        return locale != null ? locale : Locale.FRENCH;
    }
}
