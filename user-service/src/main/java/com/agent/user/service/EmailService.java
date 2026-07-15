package com.agent.user.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class EmailService {

    private final RestTemplate restTemplate;
    private final String resendApiKey;
    private final String fromAddress;

    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    public EmailService(
            @Value("${resend.api-key}") String resendApiKey,
            @Value("${resend.from:BankAgent <noreply@bankagent.online>}") String fromAddress) {
        this.resendApiKey = resendApiKey;
        this.fromAddress = fromAddress;
        this.restTemplate = new RestTemplate();
    }

    public void sendVerificationCode(String toEmail, String code) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            String htmlContent = """
                    <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto;">
                        <div style="background: #111827; padding: 24px; border-radius: 12px 12px 0 0;">
                            <h1 style="color: #fff; margin: 0; font-size: 20px;">BankAgent</h1>
                        </div>
                        <div style="background: #fff; padding: 32px 24px; border: 1px solid #e5e7eb; border-top: none; border-radius: 0 0 12px 12px;">
                            <h2 style="color: #111827; font-size: 18px; margin: 0 0 8px;">Verification Code</h2>
                            <p style="color: #6b7280; font-size: 14px; margin: 0 0 24px;">
                                Use the code below to complete your action. This code is valid for <strong>5 minutes</strong>.
                            </p>
                            <div style="background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; text-align: center; margin-bottom: 24px;">
                                <span style="font-size: 32px; font-weight: 700; color: #111827; letter-spacing: 8px;">%s</span>
                            </div>
                            <p style="color: #9ca3af; font-size: 12px; margin: 0;">
                                If you did not request this code, please ignore this email.
                            </p>
                        </div>
                    </div>
                    """.formatted(code);

            ResendEmailRequest request = new ResendEmailRequest();
            request.setFrom(fromAddress);
            request.setTo(List.of(toEmail));
            request.setSubject("[BankAgent] Email Verification Code");
            request.setHtml(htmlContent);

            HttpEntity<ResendEmailRequest> entity = new HttpEntity<>(request, headers);
            restTemplate.postForEntity(RESEND_API_URL, entity, String.class);

            log.info("Verification code email sent to {} via Resend", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification code email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send email, please try again later");
        }
    }

    @Data
    private static class ResendEmailRequest {
        private String from;
        private List<String> to;
        private String subject;
        private String html;
    }
}
