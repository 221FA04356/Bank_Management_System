package com.bank.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "money_requests")
public class MoneyRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long requesterId, payerId;
    public BigDecimal amount;
    public String note;
    /** PENDING, PAID, DECLINED, CANCELLED */
    public String status = "PENDING";
    public LocalDateTime createdAt = LocalDateTime.now();
}
