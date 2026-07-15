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
                    <!DOCTYPE html>
                    <html>
                    <head><meta charset="utf-8"></head>
                    <body style="margin:0;padding:24px 16px;">
                    <table width="100%%" cellpadding="0" cellspacing="0">
                      <tr>
                        <td>
                          <table width="100%%" cellpadding="0" cellspacing="0" style="background:#ffffff;border:1px solid #e5e7eb;border-radius:16px;max-width:440px;margin:0 auto;">
                            <tr>
                              <td style="padding:40px 32px 36px;">
                                <table width="100%%" cellpadding="0" cellspacing="0">
                                  <!-- Brand -->
                                  <tr>
                                    <td style="color:#64748b;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,sans-serif;font-size:15px;font-weight:500;letter-spacing:0.5px;padding-bottom:32px;">BankAgent</td>
                                  </tr>
                                  <!-- Title -->
                                  <tr>
                                    <td style="color:#0f172a;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,sans-serif;font-size:26px;font-weight:700;line-height:1.2;padding-bottom:12px;">Your verification code</td>
                                  </tr>
                                  <!-- Description -->
                                  <tr>
                                    <td style="color:#64748b;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,sans-serif;font-size:15px;line-height:1.6;padding-bottom:32px;">
                                      Use the code below to complete your action. This code is valid for <strong style="color:#334155;">5 minutes</strong>.
                                    </td>
                                  </tr>
                                  <!-- Code box -->
                                  <tr>
                                    <td align="center" style="padding-bottom:32px;">
                                      <table cellpadding="0" cellspacing="0" style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;width:100%%;">
                                        <tr>
                                          <td align="center" style="padding:28px 24px;">
                                            <span style="font-family:'SF Mono','Fira Code','Fira Mono','Cascadia Code','Consolas','Monaco','Courier New',monospace;font-size:38px;font-weight:700;color:#0f172a;letter-spacing:14px;line-height:1;">%s</span>
                                          </td>
                                        </tr>
                                      </table>
                                    </td>
                                  </tr>
                                  <!-- Footnote -->
                                  <tr>
                                    <td style="color:#475569;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,sans-serif;font-size:13px;line-height:1.5;padding-bottom:28px;">
                                      If you did not request this code, please ignore this email.
                                    </td>
                                  </tr>
                                  <!-- Divider -->
                                  <tr>
                                    <td style="padding-bottom:20px;">
                                      <table width="100%%" cellpadding="0" cellspacing="0">
                                        <tr><td style="border-top:1px solid #f1f5f9;"></td></tr>
                                      </table>
                                    </td>
                                  </tr>
                                  <!-- Footer -->
                                  <tr>
                                    <td style="color:#94a3b8;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,sans-serif;font-size:12px;">BankAgent Team &copy; 2026</td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                    </table>
                    </body>
                    </html>
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
