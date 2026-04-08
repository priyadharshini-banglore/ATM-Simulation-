package com.atm.model;

public class Account {
    private String accountNumber;
    private String name;
    private String hashedPin;
    private double balance;
    private boolean isBlocked;

    public Account() {}

    public Account(String accountNumber, String name, String hashedPin, double balance) {
        this.accountNumber = accountNumber;
        this.name = name;
        this.hashedPin = hashedPin;
        this.balance = balance;
        this.isBlocked = false;
    }

    // Getters and Setters
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHashedPin() { return hashedPin; }
    public void setHashedPin(String hashedPin) { this.hashedPin = hashedPin; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public boolean isBlocked() { return isBlocked; }
    public void setBlocked(boolean blocked) { isBlocked = blocked; }

    @Override
    public String toString() {
        return "Account{accountNumber='" + accountNumber + "', name='" + name +
               "', balance=" + balance + ", isBlocked=" + isBlocked + "}";
    }
}
