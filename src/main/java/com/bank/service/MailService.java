package com.bank.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Every email in the system is sent FROM the one bank address to ONE customer at a time.
 * (Person A and Person B always get their own separate emails.)
 */
@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    @Autowired ObjectProvider<JavaMailSender> sender;
    @Value("${spring.mail.host:}") String host;
    @Value("${app.mail-from}") String from;
    @Value("${app.mail-from-name:JavaBank}") String fromName;

    @Async
    public void send(String to, String subject, String body) {
        if (host == null || host.isBlank()) {
            log.warn("SMTP not configured -> EMAIL to {} | {} | {}", to, subject, body);
            return;
        }
        try {
            JavaMailSender s = sender.getObject();
            MimeMessage m = s.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, false, "UTF-8");
            h.setFrom(from, fromName);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(body, false);
            s.send(m);
            log.info("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            log.error("Could not send email to {}: {}", to, e.getMessage());
        }
    }
}
