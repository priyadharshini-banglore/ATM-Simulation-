package com.atm;

import com.atm.config.DatabaseConfig;
import com.atm.service.AccountService;
import com.atm.service.DataSeeder;
import com.atm.service.TransactionService;
import com.atm.web.WebServer;

/**
 * Main entry point for the ATM Simulation System.
 * Starts the Web UI on port 8080 and keeps the console ATM available.
 */
public class Main {

    public static void main(String[] args) {
        // 1. Connect to MongoDB
        DatabaseConfig.connect();

        // 2. Initialize services
        AccountService accountService     = new AccountService();
        TransactionService txnService     = new TransactionService();

        // 3. Seed sample data if DB is empty
        DataSeeder.seed(accountService);

        // 4. Determine mode: web (default) or console
        boolean webMode = true;
        for (String arg : args) {
            if ("--console".equalsIgnoreCase(arg)) {
                webMode = false;
            }
        }

        if (webMode) {
            // Start the Web UI
            try {
                int port = 8080;
                // Allow custom port via --port=XXXX
                for (String arg : args) {
                    if (arg.startsWith("--port=")) {
                        port = Integer.parseInt(arg.substring(7));
                    }
                }
                WebServer webServer = new WebServer(accountService, txnService);
                webServer.start(port);

                // Add shutdown hook to clean up
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    webServer.stop();
                    DatabaseConfig.close();
                }));

                System.out.println("  Press Ctrl+C to stop the server.\n");

                // Keep the main thread alive
                Thread.currentThread().join();
            } catch (Exception e) {
                System.err.println("✘ Failed to start web server: " + e.getMessage());
                e.printStackTrace();
                DatabaseConfig.close();
            }
        } else {
            // Classic console ATM
            ATM atm = new ATM(accountService, txnService);
            atm.start();
            DatabaseConfig.close();
        }
    }
}
