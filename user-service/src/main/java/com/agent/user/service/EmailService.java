package com.agent.user.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendVerificationCode(String toEmail, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("1564549374@qq.com");
            helper.setTo(toEmail);
            helper.setSubject("[BankAgent] Email Verification Code");

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

            helper.setText(htmlContent, true);
            mailSender.send(message);

            log.info("Verification code email sent to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send verification code email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send email, please try again later");
        }
    }
}
