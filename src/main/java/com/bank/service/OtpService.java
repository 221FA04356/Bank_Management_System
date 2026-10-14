package com.bank.service;

import com.bank.model.Customer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

@Service
public class OtpService {
    /** A pending action waiting for OTP confirmation. Lives in the HTTP session. */
    public static class Pending implements Serializable {
        public String purpose, to, code, channel;
        public Long ref;
        public BigDecimal amount;
        public Long customerId;
        public Instant expires;
        public int tries;
    }

    @Autowired MailService mail;
    private final SecureRandom rnd = new SecureRandom();

    public Pending issue(Customer c, String purpose, BigDecimal amount, String to) {
        Pending p = new Pending();
        p.purpose = purpose; p.amount = amount; p.to = to; p.customerId = c.id;
        p.code = String.format("%06d", rnd.nextInt(1_000_000));
        p.expires = Instant.now().plusSeconds(300);
        String what = switch (purpose) {
            case "WITHDRAW" -> "withdrawal of Rs. " + amount;
            case "TRANSFER" -> "transfer of Rs. " + amount + " to account " + to;
            default -> "unlocking your card";
        };
        mail.send(c.email, "Your JavaBank OTP: " + p.code,
                "Hello " + c.name + ",\n\nYour one-time password for " + what + " is " + p.code
                + ".\nIt is valid for 5 minutes. Never share it with anyone.\n"
                + "If you did not request this, please ignore this email.\n\n- JavaBank");
        return p;
    }

    /** @return null when the code is correct, otherwise an error message */
    public String check(Pending p, String code) {
        if (Instant.now().isAfter(p.expires)) return "OTP expired. Please start again.";
        if (p.tries >= 3) return "Too many wrong attempts. Please start again.";
        p.tries++;
        byte[] a = p.code.getBytes(), b = (code == null ? "" : code.trim()).getBytes();
        return MessageDigest.isEqual(a, b) ? null : "Incorrect OTP.";
    }

    public boolean dead(Pending p) {
        return p.tries >= 3 || Instant.now().isAfter(p.expires);
    }
}
