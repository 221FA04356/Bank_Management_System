package com.bank.service;

import com.bank.model.*;
import com.bank.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;

/** One call = in-app notification + real-time WebSocket push + (optional) email from the bank address. */
@Service
public class NotificationService {
    @Autowired NotificationRepo repo;
    @Autowired CustomerRepo customers;
    @Autowired MailService mail;
    @Autowired SimpMessagingTemplate ws;

    public void send(Customer c, String type, String title, String message, boolean email) {
        Notification n = new Notification();
        n.customerId = c.id; n.type = type; n.title = title; n.message = message;
        repo.save(n);
        Long id = c.id; String to = c.email, name = c.name;
        afterCommit(() -> {
            push(id, type, title, message);
            if (email) mail.send(to, "JavaBank: " + title,
                    "Hello " + name + ",\n\n" + message
                    + "\n\nThis is an automated message from JavaBank. We never ask for your PIN or OTP.\n- JavaBank");
        });
    }

    public void sendAdmins(String title, String message) {
        for (Customer a : customers.findByRoleOrderByIdDesc("ADMIN")) send(a, "SYSTEM", title, message, false);
    }

    private void push(Long uid, String type, String title, String message) {
        Map<String, Object> p = new HashMap<>();
        p.put("type", type); p.put("title", title); p.put("message", message);
        p.put("unread", repo.countByCustomerIdAndSeenFalse(uid));
        try { ws.convertAndSendToUser(uid.toString(), "/queue/events", p); } catch (Exception ignored) { }
    }

    /** Waits for the DB transaction to commit so the browser never refreshes to stale data. */
    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { r.run(); }
            });
        } else r.run();
    }
}
