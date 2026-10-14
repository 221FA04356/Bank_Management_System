package com.bank.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name = "transactions")
public class Txn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long customerId;
    public String type;
    public BigDecimal amount, balanceAfter;
    public String note;
    public LocalDateTime createdAt = LocalDateTime.now();
}
