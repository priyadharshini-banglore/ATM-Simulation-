package com.atm.model;

import java.time.LocalDateTime;

public class Transaction {
    private String id;
    private String accountNumber;
    private String type;       // deposit, withdraw, transfer_in, transfer_out, pin_change
    private double amount;
    private LocalDateTime date;
    private double balanceAfter;
    private String note;       // e.g. "Transfer to 67890"

    public Transaction() {}

    public Transaction(String id, String accountNumber, String type,
                       double amount, LocalDateTime date, double balanceAfter, String note) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.type = type;
        this.amount = amount;
        this.date = date;
        this.balanceAfter = balanceAfter;
        this.note = note;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public LocalDateTime getDate() { return date; }
    public void setDate(LocalDateTime date) { this.date = date; }

    public double getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(double balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
