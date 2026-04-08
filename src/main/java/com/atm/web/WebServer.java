package com.atm.web;

import com.atm.service.AccountService;
import com.atm.service.TransactionService;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server for the ATM Web UI.
 * Uses JDK's built-in com.sun.net.httpserver — zero extra dependencies.
 */
public class WebServer {

    private final AccountService accountService;
    private final TransactionService transactionService;
    private HttpServer server;

    public WebServer(AccountService accountService, TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // REST API endpoints
        server.createContext("/api/", new ApiHandler(accountService, transactionService));

        // Static file — serve index.html for everything else
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String contentType = "text/html";

            // Determine resource path
            String resourcePath = "/static/index.html";
            if (path.endsWith(".css")) {
                contentType = "text/css";
                resourcePath = "/static" + path;
            } else if (path.endsWith(".js")) {
                contentType = "application/javascript";
                resourcePath = "/static" + path;
            } else if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".ico")) {
                contentType = path.endsWith(".png") ? "image/png" : "image/jpeg";
                resourcePath = "/static" + path;
            }

            InputStream is = getClass().getResourceAsStream(resourcePath);
            if (is == null) {
                // Fallback: serve index.html for SPA routing
                is = getClass().getResourceAsStream("/static/index.html");
                contentType = "text/html";
            }

            if (is != null) {
                byte[] bytes = is.readAllBytes();
                is.close();
                exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            } else {
                String msg = "404 Not Found";
                exchange.sendResponseHeaders(404, msg.length());
                OutputStream os = exchange.getResponseBody();
                os.write(msg.getBytes());
                os.close();
            }
        });

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println();
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   🌐 ATM Web UI is LIVE!                 ║");
        System.out.println("║   → http://localhost:" + port + "                ║");
        System.out.println("╚══════════════════════════════════════════╝");
        System.out.println();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("Web server stopped.");
        }
    }
}
