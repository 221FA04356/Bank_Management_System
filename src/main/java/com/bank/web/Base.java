package com.bank.web;

import com.bank.model.Customer;
import com.bank.repo.CustomerRepo;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.math.RoundingMode;

public abstract class Base {
    @Autowired protected CustomerRepo customers;

    protected Long uid(HttpSession s) { return (Long) s.getAttribute("uid"); }
    protected Customer me(HttpSession s) { return customers.findById(uid(s)).orElseThrow(); }

    /** Parses a positive money amount (max 1 crore). Returns null if invalid. */
    protected static BigDecimal amount(String s) {
        try {
            BigDecimal a = new BigDecimal(s.trim()).setScale(2, RoundingMode.HALF_UP);
            return a.signum() > 0 && a.compareTo(new BigDecimal("10000000")) <= 0 ? a : null;
        } catch (Exception e) { return null; }
    }

    protected static boolean pin4(String p) { return p != null && p.matches("\\d{4}"); }

    /** A half-finished two-step action (transfer review / add beneficiary). Expires after 5 minutes. */
    public static class Draft implements Serializable {
        public String to, name, channel = "Transfer";
        public BigDecimal amount;
        public Long requestId;
        public Instant created = Instant.now();

        public long left() { return Math.max(0, 300 - Duration.between(created, Instant.now()).getSeconds()); }
    }

    /** "Ravi Kumar" -> "Ra*** Ku***" (shown when confirming someone else's account). */
    protected static String maskName(String n) {
        StringBuilder b = new StringBuilder();
        for (String w : n.trim().split("\\s+")) b.append(w.length() <= 2 ? w : w.substring(0, 2)).append("*** ");
        return b.toString().trim();
    }
}
