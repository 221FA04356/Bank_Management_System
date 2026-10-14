package com.bank.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "customers")
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public String name, fatherName, dob, gender;
    @Column(unique = true) public String email;
    public String marital, address, city, state, pincode, religion, category, income, education, occupation;
    @Column(unique = true) public String pan;
    @Column(unique = true) public String aadhar;
    public String seniorCitizen, existingAccount;
    public String accountType, services;
    @Column(unique = true) public String cardNumber;
    @Column(unique = true) public String accountNumber;
    public String pinHash;
    public BigDecimal balance = BigDecimal.ZERO;
    public int failedAttempts;
    public boolean locked;
    public BigDecimal dailyLimit = new BigDecimal("50000");
    public String role = "USER";
    public LocalDateTime createdAt = LocalDateTime.now();
}
