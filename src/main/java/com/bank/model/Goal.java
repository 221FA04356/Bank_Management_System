package com.bank.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.math.RoundingMode;

@Entity @Table(name = "goals")
public class Goal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long ownerId;
    public String name;
    public BigDecimal target, saved = BigDecimal.ZERO;

    public int percent() {
        if (target == null || target.signum() == 0) return 0;
        return Math.min(100, saved.multiply(BigDecimal.valueOf(100)).divide(target, 0, RoundingMode.DOWN).intValue());
    }
}
