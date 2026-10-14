package com.bank.config;

import com.bank.model.Customer;
import com.bank.repo.CustomerRepo;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates (or refreshes) the admin login at startup from ADMIN_CARD / ADMIN_PIN. */
@Component
public class AdminSeed implements CommandLineRunner {
    @Autowired CustomerRepo customers;
    @Autowired BCryptPasswordEncoder enc;
    @Value("${app.admin-card}") String card;
    @Value("${app.admin-pin}") String pin;

    @Override public void run(String... args) {
        if (pin == null || pin.isBlank()) {
            throw new IllegalStateException("ADMIN_PIN must be set in .env or deployment environment variables.");
        }
        Customer a = customers.findByCardNumber(card).orElseGet(Customer::new);
        a.cardNumber = card;
        a.accountNumber = "ADMIN-" + card;
        a.name = "Administrator";
        a.email = "admin@javabank.local";
        a.role = "ADMIN";
        a.accountType = "Admin";
        a.pinHash = enc.encode(pin);
        a.locked = false;
        a.failedAttempts = 0;
        customers.save(a);
    }
}
