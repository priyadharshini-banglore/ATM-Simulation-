package com.atm.service;

import com.atm.config.DatabaseConfig;
import com.atm.model.Transaction;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TransactionService {

    private final MongoCollection<Document> transactions;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TransactionService() {
        this.transactions = DatabaseConfig.getDatabase().getCollection("transactions");
    }

    /**
     * Record a new transaction into MongoDB.
     */
    public void addTransaction(String accountNumber, String type,
                               double amount, double balanceAfter, String note) {
        String txnId = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        LocalDateTime now = LocalDateTime.now();

        Document doc = new Document("_id", txnId)
                .append("accountNumber", accountNumber)
                .append("type", type)
                .append("amount", amount)
                .append("date", now.format(FORMATTER))
                .append("balanceAfter", balanceAfter)
                .append("note", note != null ? note : "");

        transactions.insertOne(doc);
    }

    /**
     * Fetch the last N transactions for an account (most recent first).
     */
    public List<Transaction> getMiniStatement(String accountNumber, int limit) {
        List<Transaction> list = new ArrayList<>();

        transactions.find(Filters.eq("accountNumber", accountNumber))
                .sort(Sorts.descending("date"))
                .limit(limit)
                .forEach(doc -> {
                    Transaction t = new Transaction();
                    t.setId(doc.getString("_id"));
                    t.setAccountNumber(doc.getString("accountNumber"));
                    t.setType(doc.getString("type"));
                    Object amt = doc.get("amount");
                    t.setAmount(amt instanceof Number ? ((Number) amt).doubleValue() : 0.0);
                    String dateStr = doc.getString("date");
                    t.setDate(dateStr != null
                            ? LocalDateTime.parse(dateStr, FORMATTER)
                            : LocalDateTime.now());
                    Object bal = doc.get("balanceAfter");
                    t.setBalanceAfter(bal instanceof Number ? ((Number) bal).doubleValue() : 0.0);
                    t.setNote(doc.getString("note"));
                    list.add(t);
                });

        return list;
    }
}
