package com.atm.service;

import com.atm.config.DatabaseConfig;
import com.atm.model.Account;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Seeds the ATM_DB with sample accounts on first run.
 * Run once — safe to call again (skips existing accounts).
 */
public class DataSeeder {

    public static void seed(AccountService accountService) {
        System.out.println("   Checking for seed data...");

        seedAccount(accountService, "12345", "Priya Sharma",   "1234",  10000.0);
        seedAccount(accountService, "67890", "Rahul Mehta",    "5678",  25000.0);
        seedAccount(accountService, "11223", "Anita Verma",    "9012",   5000.0);
        seedAccount(accountService, "44556", "Vikram Singh",   "3456",  50000.0);

        System.out.println("   Seed check complete.\n");
    }

    private static void seedAccount(AccountService svc, String accNo,
                                    String name, String pin, double balance) {
        if (!svc.accountExists(accNo)) {
            String hashed = BCrypt.hashpw(pin, BCrypt.gensalt(12));
            Account acc = new Account(accNo, name, hashed, balance);
            svc.createAccount(acc);
            System.out.println("   Created account: " + accNo + " (" + name + ")");
        }
    }
}
