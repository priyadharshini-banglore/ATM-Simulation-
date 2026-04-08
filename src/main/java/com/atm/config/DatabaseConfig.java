package com.atm.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public class DatabaseConfig {

    private static final String CONNECTION_URI = "mongodb://localhost:27017";
    private static final String DB_NAME = "ATM_DB";

    private static MongoClient mongoClient;
    private static MongoDatabase database;

    public static MongoDatabase getDatabase() {
        if (database == null) {
            connect();
        }
        return database;
    }

    public static void connect() {
        try {
            System.out.println("╔══════════════════════════════════════╗");
            System.out.println("║   Connecting to MongoDB...           ║");
            System.out.println("╚══════════════════════════════════════╝");
            mongoClient = MongoClients.create(CONNECTION_URI);
            database = mongoClient.getDatabase(DB_NAME);
            // Ping the database to verify connection
            database.runCommand(new org.bson.Document("ping", 1));
            System.out.println("✔  Connected to ATM_DB successfully!\n");
        } catch (Exception e) {
            System.err.println("✘  Failed to connect to MongoDB: " + e.getMessage());
            System.err.println("   Make sure MongoDB is running on localhost:27017");
            System.exit(1);
        }
    }

    public static void close() {
        if (mongoClient != null) {
            mongoClient.close();
            System.out.println("\n✔  Database connection closed. Goodbye!");
        }
    }
}
