package com.atm.web;

import com.atm.model.Account;
import com.atm.model.Transaction;
import com.atm.service.AccountService;
import com.atm.service.TransactionService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all /api/* REST endpoints for the ATM Web UI.
 */
public class ApiHandler implements HttpHandler {

    private final AccountService accountService;
    private final TransactionService transactionService;

    // Simple in-memory session store:  token -> accountNumber
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    public ApiHandler(AccountService accountService, TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // CORS headers
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, X-Session-Token");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        try {
            String response = switch (path) {
                case "/api/login"          -> handleLogin(exchange);
                case "/api/logout"         -> handleLogout(exchange);
                case "/api/balance"        -> handleBalance(exchange);
                case "/api/deposit"        -> handleDeposit(exchange);
                case "/api/withdraw"       -> handleWithdraw(exchange);
                case "/api/transfer"       -> handleTransfer(exchange);
                case "/api/change-pin"     -> handleChangePin(exchange);
                case "/api/mini-statement" -> handleMiniStatement(exchange);
                case "/api/validate-recipient" -> handleValidateRecipient(exchange);
                default -> {
                    sendResponse(exchange, 404, "{\"error\":\"Endpoint not found\"}");
                    yield null;
                }
            };
            if (response != null) {
                sendResponse(exchange, 200, response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // ── LOGIN ───────────────────────────────────────────────────────────────

    private String handleLogin(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJsonBody(ex);
        String accountNumber = body.get("accountNumber");
        String pin = body.get("pin");

        if (accountNumber == null || pin == null) {
            return "{\"success\":false,\"message\":\"Account number and PIN are required\"}";
        }

        Account account = accountService.fetchAccount(accountNumber);
        if (account == null) {
            return "{\"success\":false,\"message\":\"Account not found\"}";
        }
        if (account.isBlocked()) {
            return "{\"success\":false,\"message\":\"Account is BLOCKED. Contact your bank.\"}";
        }
        if (!accountService.verifyPin(pin, account.getHashedPin())) {
            return "{\"success\":false,\"message\":\"Incorrect PIN\"}";
        }

        // Create session
        String token = UUID.randomUUID().toString();
        sessions.put(token, accountNumber);

        return String.format(
                "{\"success\":true,\"token\":\"%s\",\"name\":\"%s\",\"accountNumber\":\"%s\",\"balance\":%.2f}",
                token, escapeJson(account.getName()), escapeJson(account.getAccountNumber()), account.getBalance()
        );
    }

    // ── LOGOUT ──────────────────────────────────────────────────────────────

    private String handleLogout(HttpExchange ex) {
        String token = getSessionToken(ex);
        if (token != null) sessions.remove(token);
        return "{\"success\":true}";
    }

    // ── BALANCE ─────────────────────────────────────────────────────────────

    private String handleBalance(HttpExchange ex) throws IOException {
        String accNo = getAuthenticatedAccount(ex);
        if (accNo == null) return authError();

        Account account = accountService.fetchAccount(accNo);
        return String.format(
                "{\"success\":true,\"balance\":%.2f,\"name\":\"%s\",\"accountNumber\":\"%s\"}",
                account.getBalance(), escapeJson(account.getName()), escapeJson(account.getAccountNumber())
        );
    }

    // ── DEPOSIT ─────────────────────────────────────────────────────────────

    private String handleDeposit(HttpExchange ex) throws IOException {
        String accNo = getAuthenticatedAccount(ex);
        if (accNo == null) return authError();

        Map<String, String> body = parseJsonBody(ex);
        double amount;
        try {
            amount = Double.parseDouble(body.getOrDefault("amount", "0"));
        } catch (NumberFormatException e) {
            return "{\"success\":false,\"message\":\"Invalid amount\"}";
        }
        if (amount <= 0) return "{\"success\":false,\"message\":\"Amount must be positive\"}";

        Account account = accountService.fetchAccount(accNo);
        double newBalance = account.getBalance() + amount;

        if (accountService.updateBalance(accNo, newBalance)) {
            transactionService.addTransaction(accNo, "deposit", amount, newBalance, "Cash deposit via Web");
            return String.format("{\"success\":true,\"newBalance\":%.2f,\"message\":\"₹%.2f deposited successfully\"}", newBalance, amount);
        }
        return "{\"success\":false,\"message\":\"Deposit failed. Try again.\"}";
    }

    // ── WITHDRAW ────────────────────────────────────────────────────────────

    private String handleWithdraw(HttpExchange ex) throws IOException {
        String accNo = getAuthenticatedAccount(ex);
        if (accNo == null) return authError();

        Map<String, String> body = parseJsonBody(ex);
        double amount;
        try {
            amount = Double.parseDouble(body.getOrDefault("amount", "0"));
        } catch (NumberFormatException e) {
            return "{\"success\":false,\"message\":\"Invalid amount\"}";
        }
        if (amount <= 0) return "{\"success\":false,\"message\":\"Amount must be positive\"}";

        Account account = accountService.fetchAccount(accNo);
        if (amount > account.getBalance()) {
            return String.format("{\"success\":false,\"message\":\"Insufficient balance. Available: ₹%.2f\"}", account.getBalance());
        }

        double newBalance = account.getBalance() - amount;
        if (accountService.updateBalance(accNo, newBalance)) {
            transactionService.addTransaction(accNo, "withdraw", amount, newBalance, "Cash withdrawal via Web");
            return String.format("{\"success\":true,\"newBalance\":%.2f,\"message\":\"₹%.2f withdrawn successfully\"}", newBalance, amount);
        }
        return "{\"success\":false,\"message\":\"Withdrawal failed. Try again.\"}";
    }

    // ── TRANSFER ────────────────────────────────────────────────────────────

    private String handleTransfer(HttpExchange ex) throws IOException {
        String accNo = getAuthenticatedAccount(ex);
        if (accNo == null) return authError();

        Map<String, String> body = parseJsonBody(ex);
        String recipientAccNo = body.get("recipientAccount");
        double amount;
        try {
            amount = Double.parseDouble(body.getOrDefault("amount", "0"));
        } catch (NumberFormatException e) {
            return "{\"success\":false,\"message\":\"Invalid amount\"}";
        }
        if (amount <= 0) return "{\"success\":false,\"message\":\"Amount must be positive\"}";
        if (recipientAccNo == null || recipientAccNo.isBlank()) {
            return "{\"success\":false,\"message\":\"Recipient account number is required\"}";
        }
        if (recipientAccNo.equals(accNo)) {
            return "{\"success\":false,\"message\":\"Cannot transfer to your own account\"}";
        }

        Account sender = accountService.fetchAccount(accNo);
        Account recipient = accountService.fetchAccount(recipientAccNo);

        if (recipient == null) return "{\"success\":false,\"message\":\"Recipient account not found\"}";
        if (recipient.isBlocked()) return "{\"success\":false,\"message\":\"Recipient account is blocked\"}";
        if (amount > sender.getBalance()) {
            return String.format("{\"success\":false,\"message\":\"Insufficient balance. Available: ₹%.2f\"}", sender.getBalance());
        }

        // Debit sender
        double senderNewBal = sender.getBalance() - amount;
        accountService.updateBalance(accNo, senderNewBal);
        transactionService.addTransaction(accNo, "transfer_out", amount, senderNewBal, "Transfer to " + recipientAccNo);

        // Credit receiver
        double recipientNewBal = recipient.getBalance() + amount;
        accountService.updateBalance(recipientAccNo, recipientNewBal);
        transactionService.addTransaction(recipientAccNo, "transfer_in", amount, recipientNewBal, "Transfer from " + accNo);

        return String.format(
                "{\"success\":true,\"newBalance\":%.2f,\"recipientName\":\"%s\",\"message\":\"₹%.2f transferred to %s successfully\"}",
                senderNewBal, escapeJson(recipient.getName()), amount, escapeJson(recipient.getName())
        );
    }

    // ── CHANGE PIN ──────────────────────────────────────────────────────────

    private String handleChangePin(HttpExchange ex) throws IOException {
        String accNo = getAuthenticatedAccount(ex);
        if (accNo == null) return authError();

        Map<String, String> body = parseJsonBody(ex);
        String currentPin = body.get("currentPin");
        String newPin = body.get("newPin");

        if (currentPin == null || newPin == null) {
            return "{\"success\":false,\"message\":\"Current PIN and new PIN are required\"}";
        }
        if (!newPin.matches("\\d{4}")) {
            return "{\"success\":false,\"message\":\"New PIN must be exactly 4 digits\"}";
        }

        Account account = accountService.fetchAccount(accNo);
        if (!accountService.verifyPin(currentPin, account.getHashedPin())) {
            return "{\"success\":false,\"message\":\"Incorrect current PIN\"}";
        }

        if (accountService.changePin(accNo, newPin)) {
            transactionService.addTransaction(accNo, "pin_change", 0, account.getBalance(), "PIN changed via Web");
            return "{\"success\":true,\"message\":\"PIN changed successfully\"}";
        }
        return "{\"success\":false,\"message\":\"Failed to change PIN. Try again.\"}";
    }

    // ── MINI STATEMENT ──────────────────────────────────────────────────────

    private String handleMiniStatement(HttpExchange ex) {
        String accNo = getAuthenticatedAccount(ex);
        if (accNo == null) return authError();

        List<Transaction> txns = transactionService.getMiniStatement(accNo, 10);

        StringBuilder sb = new StringBuilder("{\"success\":true,\"transactions\":[");
        for (int i = 0; i < txns.size(); i++) {
            Transaction t = txns.get(i);
            if (i > 0) sb.append(",");
            sb.append(String.format(
                    "{\"id\":\"%s\",\"type\":\"%s\",\"amount\":%.2f,\"balanceAfter\":%.2f,\"date\":\"%s\",\"note\":\"%s\"}",
                    escapeJson(t.getId()),
                    escapeJson(formatType(t.getType())),
                    t.getAmount(),
                    t.getBalanceAfter(),
                    t.getDate().toString().replace("T", " "),
                    escapeJson(t.getNote() != null ? t.getNote() : "")
            ));
        }
        sb.append("]}");
        return sb.toString();
    }

    // ── VALIDATE RECIPIENT ──────────────────────────────────────────────────

    private String handleValidateRecipient(HttpExchange ex) throws IOException {
        Map<String, String> body = parseJsonBody(ex);
        String recipientAccNo = body.get("accountNumber");

        if (recipientAccNo == null || recipientAccNo.isBlank()) {
            return "{\"success\":false,\"message\":\"Account number required\"}";
        }

        Account recipient = accountService.fetchAccount(recipientAccNo);
        if (recipient == null) {
            return "{\"success\":false,\"message\":\"Account not found\"}";
        }
        if (recipient.isBlocked()) {
            return "{\"success\":false,\"message\":\"Account is blocked\"}";
        }

        return String.format("{\"success\":true,\"name\":\"%s\"}", escapeJson(recipient.getName()));
    }

    // ── HELPERS ─────────────────────────────────────────────────────────────

    private String getSessionToken(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("X-Session-Token");
        return header;
    }

    private String getAuthenticatedAccount(HttpExchange ex) {
        String token = getSessionToken(ex);
        if (token == null) return null;
        return sessions.get(token);
    }

    private String authError() {
        return "{\"success\":false,\"message\":\"Session expired. Please login again.\",\"authError\":true}";
    }

    private Map<String, String> parseJsonBody(HttpExchange ex) throws IOException {
        InputStream is = ex.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> map = new HashMap<>();

        if (body.isBlank()) return map;

        // Simple JSON parser for flat { "key": "value" } objects
        body = body.trim();
        if (body.startsWith("{")) body = body.substring(1);
        if (body.endsWith("}")) body = body.substring(0, body.length() - 1);

        // Split by comma, handling quoted strings
        String[] pairs = body.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replace("\"", "");
                String value = kv[1].trim().replace("\"", "");
                map.put(key, value);
            }
        }
        return map;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
