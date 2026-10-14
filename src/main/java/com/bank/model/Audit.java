package com.bank.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name = "audit_log")
public class Audit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String actor, action;
    @Column(length = 500) public String detail;
    public LocalDateTime createdAt = LocalDateTime.now();
}
