package com.bank.service;

import com.bank.model.*;
import com.bank.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class Jobs {
    @Autowired StandingRepo standing;
    @Autowired CustomerRepo customers;
    @Autowired BankService bank;
    @Autowired NotificationService notes;

    /** Every day 06:00 IST: run standing orders that are due today. */
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Kolkata")
    public void daily() { runStandingOrders(); }

    /** 1st of every month 00:30 IST: credit interest to savings accounts. */
    @Scheduled(cron = "0 30 0 1 * *", zone = "Asia/Kolkata")
    public void monthly() { bank.creditInterest(); }

    public int runStandingOrders() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        int done = 0;
        for (StandingOrder s : standing.findByActiveTrue()) {
            int day = Math.min(s.dayOfMonth, today.lengthOfMonth());
            if (today.getDayOfMonth() != day || today.equals(s.lastRun)) continue;
            String err = bank.transfer(s.ownerId, s.toAccount, s.amount, "Scheduled transfer");
            if (err == null) {
                s.lastRun = today;
                standing.save(s);
                bank.audit("SYSTEM", "STANDING_ORDER_OK", "order #" + s.id + " Rs. " + s.amount + " to " + s.toAccount);
                done++;
            } else {
                bank.audit("SYSTEM", "STANDING_ORDER_FAILED", "order #" + s.id + ": " + err);
                customers.findById(s.ownerId).ifPresent(c -> notes.send(c, "TRANSACTION", "Scheduled transfer failed",
                        "Your scheduled transfer of Rs. " + s.amount + " to account " + s.toAccount + " could not be made: " + err, true));
            }
        }
        return done;
    }
}
