package com.example.invoiceflow.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.base-url}")
    private String baseUrl;

    public void sendVerificationEmail(String to, String token) {
        String link = baseUrl + "/api/auth/verify?token=" + token;
        String html = """
                <p>Bonjour,</p>
                <p>Merci de vous être inscrit sur <strong>InvoiceFlow</strong>.</p>
                <p>Veuillez confirmer votre adresse email en cliquant sur le lien ci-dessous :</p>
                <p><a href="%s">Confirmer mon email</a></p>
                <p>Ce lien est valable 24 heures.</p>
                <p>Si vous n'avez pas créé de compte, ignorez cet email.</p>
                """.formatted(link);
        send(to, "Confirmez votre adresse email — InvoiceFlow", html);
    }

    private void send(String to, String subject, String htmlBody) {
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
    }
}
