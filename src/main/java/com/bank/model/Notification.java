package com.bank.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long customerId;
    /** SECURITY, TRANSACTION, LOAN, REQUEST, SYSTEM */
    public String type;
    public String title;
    @Column(length = 1000) public String message;
    public boolean seen;
    public LocalDateTime createdAt = LocalDateTime.now();
}
