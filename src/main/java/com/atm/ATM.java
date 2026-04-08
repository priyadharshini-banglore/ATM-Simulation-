package com.atm;

import com.atm.model.Account;
import com.atm.model.Transaction;
import com.atm.service.AccountService;
import com.atm.service.TransactionService;

import java.io.Console;
import java.util.List;
import java.util.Scanner;

/**
 * Core ATM class — handles login, menu and all banking operations.
 */
public class ATM {

    private static final int MAX_ATTEMPTS = 3;
    private static final int MINI_STMT_LIMIT = 5;

    private final AccountService accountService;
    private final TransactionService transactionService;
    private final Scanner scanner;

    private Account currentAccount;

    public ATM(AccountService accountService, TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.scanner = new Scanner(System.in);
    }

    // ── Entry Point ─────────────────────────────────────────────────────────

    public void start() {
        printBanner();
        while (true) {
            if (login()) {
                showMenu();
            }
            System.out.print("\n  Use another account? (y/n): ");
            String again = scanner.nextLine().trim().toLowerCase();
            if (!again.equals("y")) break;
        }
    }

    // ── Login ───────────────────────────────────────────────────────────────

    private boolean login() {
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║           USER AUTHENTICATION            ║");
        System.out.println("╚══════════════════════════════════════════╝");

        System.out.print("  Enter Account Number: ");
        String accNo = scanner.nextLine().trim();

        Account account = accountService.fetchAccount(accNo);
        if (account == null) {
            System.out.println("  ✘ Account not found.");
            return false;
        }
        if (account.isBlocked()) {
            System.out.println("  ✘ This account is BLOCKED. Contact your bank.");
            return false;
        }

        int attempts = 0;
        while (attempts < MAX_ATTEMPTS) {
            System.out.print("  Enter PIN: ");
            String pin = readMasked();

            if (accountService.verifyPin(pin, account.getHashedPin())) {
                currentAccount = account;
                System.out.println("\n  ✔ Login Successful! Welcome, " + account.getName() + ".\n");
                return true;
            } else {
                attempts++;
                int remaining = MAX_ATTEMPTS - attempts;
                if (remaining > 0) {
                    System.out.println("  ✘ Wrong PIN. " + remaining + " attempt(s) remaining.");
                } else {
                    System.out.println("  ✘ Too many failed attempts. Account BLOCKED.");
                    accountService.blockAccount(accNo);
                }
            }
        }
        return false;
    }

    // ── Main Menu ───────────────────────────────────────────────────────────

    private void showMenu() {
        boolean running = true;
        while (running) {
            // Refresh account from DB before showing balance
            currentAccount = accountService.fetchAccount(currentAccount.getAccountNumber());

            System.out.println("\n╔══════════════════════════════════════════╗");
            System.out.printf( "║  Account: %-31s║%n", currentAccount.getAccountNumber());
            System.out.printf( "║  Name:    %-31s║%n", currentAccount.getName());
            System.out.println("╠══════════════════════════════════════════╣");
            System.out.println("║         ATM MAIN MENU                    ║");
            System.out.println("╠══════════════════════════════════════════╣");
            System.out.println("║  1. Check Balance                        ║");
            System.out.println("║  2. Deposit Money                        ║");
            System.out.println("║  3. Withdraw Money                       ║");
            System.out.println("║  4. Transfer Money                       ║");
            System.out.println("║  5. Change PIN                           ║");
            System.out.println("║  6. Mini Statement (Last 5 Transactions) ║");
            System.out.println("║  7. Exit                                 ║");
            System.out.println("╚══════════════════════════════════════════╝");
            System.out.print("  Enter choice: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> checkBalance();
                case "2" -> deposit();
                case "3" -> withdraw();
                case "4" -> transfer();
                case "5" -> changePin();
                case "6" -> miniStatement();
                case "7" -> { running = false; System.out.println("\n  Thank you for using our ATM. Have a great day!"); }
                default  -> System.out.println("  ✘ Invalid choice. Please enter 1-7.");
            }
        }
        currentAccount = null;
    }

    // ── Operations ──────────────────────────────────────────────────────────

    private void checkBalance() {
        printSectionHeader("BALANCE ENQUIRY");
        System.out.printf("  ✔ Available Balance: ₹%.2f%n", currentAccount.getBalance());
    }

    private void deposit() {
        printSectionHeader("DEPOSIT MONEY");
        System.out.print("  Enter amount to deposit (₹): ");
        double amount = readAmount();
        if (amount <= 0) { System.out.println("  ✘ Invalid amount."); return; }

        double newBalance = currentAccount.getBalance() + amount;
        if (accountService.updateBalance(currentAccount.getAccountNumber(), newBalance)) {
            transactionService.addTransaction(
                    currentAccount.getAccountNumber(), "deposit",
                    amount, newBalance, "Cash deposit");
            currentAccount.setBalance(newBalance);
            System.out.printf("  ✔ ₹%.2f deposited successfully.%n", amount);
            System.out.printf("  ✔ New Balance: ₹%.2f%n", newBalance);
        } else {
            System.out.println("  ✘ Deposit failed. Please try again.");
        }
    }

    private void withdraw() {
        printSectionHeader("WITHDRAW MONEY");
        System.out.print("  Enter amount to withdraw (₹): ");
        double amount = readAmount();
        if (amount <= 0) { System.out.println("  ✘ Invalid amount."); return; }
        if (amount > currentAccount.getBalance()) {
            System.out.println("  ✘ Insufficient balance.");
            System.out.printf("  ✔ Available: ₹%.2f%n", currentAccount.getBalance());
            return;
        }

        double newBalance = currentAccount.getBalance() - amount;
        if (accountService.updateBalance(currentAccount.getAccountNumber(), newBalance)) {
            transactionService.addTransaction(
                    currentAccount.getAccountNumber(), "withdraw",
                    amount, newBalance, "Cash withdrawal");
            currentAccount.setBalance(newBalance);
            System.out.printf("  ✔ ₹%.2f withdrawn successfully.%n", amount);
            System.out.printf("  ✔ Remaining Balance: ₹%.2f%n", newBalance);
        } else {
            System.out.println("  ✘ Withdrawal failed. Please try again.");
        }
    }

    private void transfer() {
        printSectionHeader("TRANSFER MONEY");
        System.out.print("  Enter recipient account number: ");
        String recipientAccNo = scanner.nextLine().trim();

        if (recipientAccNo.equals(currentAccount.getAccountNumber())) {
            System.out.println("  ✘ Cannot transfer to your own account.");
            return;
        }

        Account recipient = accountService.fetchAccount(recipientAccNo);
        if (recipient == null) {
            System.out.println("  ✘ Recipient account not found.");
            return;
        }
        if (recipient.isBlocked()) {
            System.out.println("  ✘ Recipient account is blocked.");
            return;
        }

        System.out.println("  Recipient Name: " + recipient.getName());
        System.out.print("  Enter amount to transfer (₹): ");
        double amount = readAmount();
        if (amount <= 0) { System.out.println("  ✘ Invalid amount."); return; }
        if (amount > currentAccount.getBalance()) {
            System.out.println("  ✘ Insufficient balance.");
            return;
        }

        // Confirm
        System.out.printf("  Confirm transfer of ₹%.2f to %s (%s)? (y/n): ",
                amount, recipient.getName(), recipientAccNo);
        if (!scanner.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println("  Transfer cancelled.");
            return;
        }

        // Debit sender
        double senderNewBal = currentAccount.getBalance() - amount;
        accountService.updateBalance(currentAccount.getAccountNumber(), senderNewBal);
        transactionService.addTransaction(
                currentAccount.getAccountNumber(), "transfer_out",
                amount, senderNewBal, "Transfer to " + recipientAccNo);

        // Credit receiver
        double recipientNewBal = recipient.getBalance() + amount;
        accountService.updateBalance(recipientAccNo, recipientNewBal);
        transactionService.addTransaction(
                recipientAccNo, "transfer_in",
                amount, recipientNewBal, "Transfer from " + currentAccount.getAccountNumber());

        currentAccount.setBalance(senderNewBal);
        System.out.printf("  ✔ ₹%.2f transferred to %s successfully.%n", amount, recipient.getName());
        System.out.printf("  ✔ Your new balance: ₹%.2f%n", senderNewBal);
    }

    private void changePin() {
        printSectionHeader("CHANGE PIN");
        System.out.print("  Enter current PIN: ");
        String currentPin = readMasked();

        if (!accountService.verifyPin(currentPin, currentAccount.getHashedPin())) {
            System.out.println("  ✘ Incorrect current PIN.");
            return;
        }

        System.out.print("  Enter new PIN (4 digits): ");
        String newPin = readMasked();
        if (!newPin.matches("\\d{4}")) {
            System.out.println("  ✘ PIN must be exactly 4 digits.");
            return;
        }

        System.out.print("  Confirm new PIN: ");
        String confirmPin = readMasked();
        if (!newPin.equals(confirmPin)) {
            System.out.println("  ✘ PINs do not match.");
            return;
        }

        if (accountService.changePin(currentAccount.getAccountNumber(), newPin)) {
            // Refresh hashed pin in local object
            currentAccount = accountService.fetchAccount(currentAccount.getAccountNumber());
            transactionService.addTransaction(
                    currentAccount.getAccountNumber(), "pin_change",
                    0, currentAccount.getBalance(), "PIN changed");
            System.out.println("  ✔ PIN changed successfully.");
        } else {
            System.out.println("  ✘ Failed to change PIN. Please try again.");
        }
    }

    private void miniStatement() {
        printSectionHeader("MINI STATEMENT (Last " + MINI_STMT_LIMIT + " Transactions)");
        List<Transaction> txns = transactionService.getMiniStatement(
                currentAccount.getAccountNumber(), MINI_STMT_LIMIT);

        if (txns.isEmpty()) {
            System.out.println("  No transactions found.");
            return;
        }

        System.out.println("  ┌──────────────┬──────────────┬──────────────┬─────────────────────┐");
        System.out.printf( "  │ %-12s │ %-12s │ %-12s │ %-19s │%n",
                "TYPE", "AMOUNT (₹)", "BALANCE (₹)", "DATE & TIME");
        System.out.println("  ├──────────────┼──────────────┼──────────────┼─────────────────────┤");

        for (Transaction t : txns) {
            String type = formatType(t.getType());
            System.out.printf("  │ %-12s │ %12.2f │ %12.2f │ %-19s │%n",
                    type, t.getAmount(), t.getBalanceAfter(),
                    t.getDate().toString().replace("T", " ").substring(0, 19));
        }
        System.out.println("  └──────────────┴──────────────┴──────────────┴─────────────────────┘");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════╗");
        System.out.println("  ║                                          ║");
        System.out.println("  ║      🏧  ATM SIMULATION SYSTEM           ║");
        System.out.println("  ║         Powered by MongoDB               ║");
        System.out.println("  ║                                          ║");
        System.out.println("  ╚══════════════════════════════════════════╝");
        System.out.println();
    }

    private void printSectionHeader(String title) {
        System.out.println("\n  ── " + title + " " + "─".repeat(Math.max(0, 38 - title.length())));
    }

    private double readAmount() {
        try {
            return Double.parseDouble(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Reads input masking characters with '*' (works in real terminal).
     * Falls back to plain input if console is unavailable (IDE).
     */
    private String readMasked() {
        Console console = System.console();
        if (console != null) {
            char[] pinChars = console.readPassword();
            return new String(pinChars);
        } else {
            // IDE fallback — no masking
            return scanner.nextLine().trim();
        }
    }

    private String formatType(String type) {
        return switch (type) {
            case "deposit"      -> "DEPOSIT";
            case "withdraw"     -> "WITHDRAW";
            case "transfer_in"  -> "CREDIT";
            case "transfer_out" -> "DEBIT";
            case "pin_change"   -> "PIN CHANGE";
            default             -> type.toUpperCase();
        };
    }
}
