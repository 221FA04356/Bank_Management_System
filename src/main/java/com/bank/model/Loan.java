package com.bank.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name = "loans")
public class Loan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long ownerId;
    public BigDecimal principal, rate, emi;
    public int months;
    public String status;
    public LocalDateTime createdAt = LocalDateTime.now();
}
