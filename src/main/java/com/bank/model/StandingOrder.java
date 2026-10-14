package com.bank.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name = "standing_orders")
public class StandingOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long ownerId;
    public String toAccount;
    public BigDecimal amount;
    public int dayOfMonth;
    public LocalDate lastRun;
    public boolean active = true;
}
