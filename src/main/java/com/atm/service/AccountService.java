package com.atm.service;

import com.atm.config.DatabaseConfig;
import com.atm.model.Account;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

public class AccountService {

    private final MongoCollection<Document> accounts;

    public AccountService() {
        this.accounts = DatabaseConfig.getDatabase().getCollection("accounts");
    }

    /**
     * Fetch an account by account number.
     */
    public Account fetchAccount(String accountNumber) {
        Document doc = accounts.find(Filters.eq("_id", accountNumber)).first();
        if (doc == null) return null;
        return documentToAccount(doc);
    }

    /**
     * Update account balance in MongoDB.
     */
    public boolean updateBalance(String accountNumber, double newBalance) {
        var result = accounts.updateOne(
                Filters.eq("_id", accountNumber),
                Updates.set("balance", newBalance)
        );
        return result.getModifiedCount() > 0;
    }

    /**
     * Change PIN — stores BCrypt hash in DB.
     */
    public boolean changePin(String accountNumber, String newPin) {
        String hashed = BCrypt.hashpw(newPin, BCrypt.gensalt(12));
        var result = accounts.updateOne(
                Filters.eq("_id", accountNumber),
                Updates.set("pin", hashed)
        );
        return result.getModifiedCount() > 0;
    }

    /**
     * Block an account after too many failed attempts.
     */
    public void blockAccount(String accountNumber) {
        accounts.updateOne(
                Filters.eq("_id", accountNumber),
                Updates.set("isBlocked", true)
        );
    }

    /**
     * Verify PIN using BCrypt comparison.
     */
    public boolean verifyPin(String plainPin, String hashedPin) {
        try {
            return BCrypt.checkpw(plainPin, hashedPin);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Create a new account (used by seed/setup).
     */
    public boolean createAccount(Account account) {
        try {
            Document doc = new Document("_id", account.getAccountNumber())
                    .append("name", account.getName())
                    .append("pin", account.getHashedPin())
                    .append("balance", account.getBalance())
                    .append("isBlocked", account.isBlocked());
            accounts.insertOne(doc);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if account number already exists.
     */
    public boolean accountExists(String accountNumber) {
        return accounts.find(Filters.eq("_id", accountNumber)).first() != null;
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private Account documentToAccount(Document doc) {
        Account a = new Account();
        a.setAccountNumber(doc.getString("_id"));
        a.setName(doc.getString("name"));
        a.setHashedPin(doc.getString("pin"));
        Object bal = doc.get("balance");
        a.setBalance(bal instanceof Number ? ((Number) bal).doubleValue() : 0.0);
        Boolean blocked = doc.getBoolean("isBlocked");
        a.setBlocked(blocked != null && blocked);
        return a;
    }
}
