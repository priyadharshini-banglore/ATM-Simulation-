# 🏧 ATM Simulation System — MongoDB + Java

A fully functional ATM simulation built with **Java 17** and **MongoDB** as the backend database.

---

## 📁 Project Structure

```
ATM-Simulation/
├── pom.xml
└── src/main/java/com/atm/
    ├── Main.java                          # Entry point
    ├── ATM.java                           # Core ATM logic + menu
    ├── config/
    │   └── DatabaseConfig.java            # MongoDB connection
    ├── model/
    │   ├── Account.java                   # Account POJO
    │   └── Transaction.java               # Transaction POJO
    └── service/
        ├── AccountService.java            # CRUD for accounts collection
        ├── TransactionService.java        # Insert/query transactions
        └── DataSeeder.java                # Seeds sample accounts on first run
```

---

## 🗄️ MongoDB Design

**Database:** `ATM_DB`

### Collection: `accounts`
```json
{
  "_id": "12345",
  "name": "Priya Sharma",
  "pin": "<BCrypt-hashed>",
  "balance": 10000.0,
  "isBlocked": false
}
```

### Collection: `transactions`
```json
{
  "_id": "TXN4F3A1B2C3D",
  "accountNumber": "12345",
  "type": "deposit",
  "amount": 5000.0,
  "date": "2026-04-05 10:30:00",
  "balanceAfter": 15000.0,
  "note": "Cash deposit"
}
```

---

## ✅ Features

| Feature | Description |
|---|---|
| 🔐 Secure Login | Account number + PIN with BCrypt hashing |
| 🚫 Account Block | Locked after 3 wrong PIN attempts |
| 💰 Check Balance | Live fetch from MongoDB |
| 📥 Deposit | Updates balance + records transaction |
| 📤 Withdraw | Validates funds before deducting |
| 🔄 Transfer | Atomic debit/credit between two accounts |
| 🔑 Change PIN | Validates old PIN, stores new BCrypt hash |
| 📋 Mini Statement | Last 5 transactions in a formatted table |

---

## 🚀 Prerequisites

1. **Java 17+** – [Download](https://adoptium.net/)
2. **Apache Maven** – [Download](https://maven.apache.org/download.cgi)
3. **MongoDB Community Server** – [Download](https://www.mongodb.com/try/download/community)

Make sure MongoDB is running on `localhost:27017`.

---

## ▶️ How to Run

### 1. Clone / open the project
```
cd D:\DBMS_project
```

### 2. Build & run with Maven
```bash
mvn compile exec:java
```

### 3. Or package as a fat JAR and run
```bash
mvn package
java -jar target/ATM-Simulation-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## 🧪 Sample Accounts (Auto-Seeded)

| Account No | Name | PIN | Balance |
|---|---|---|---|
| 12345 | Priya Sharma | 1234 | ₹10,000 |
| 67890 | Rahul Mehta | 5678 | ₹25,000 |
| 11223 | Anita Verma | 9012 | ₹5,000 |
| 44556 | Vikram Singh | 3456 | ₹50,000 |

---

## 🔒 Security Notes

- PINs are **never stored in plain text** — BCrypt with cost factor 12 is used.
- Accounts are **automatically blocked** after 3 failed login attempts.
- All operations fetch **live data from MongoDB** (no stale cache).
