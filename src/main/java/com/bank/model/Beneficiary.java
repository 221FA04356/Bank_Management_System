package com.bank.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name = "beneficiaries")
public class Beneficiary {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Long ownerId;
    public String name, accountNumber;
}
