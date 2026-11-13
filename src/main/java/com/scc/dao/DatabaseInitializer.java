package com.scc.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseInitializer {

    private DatabaseInitializer() {
    }

    /** Creates every required table (if missing) and seeds a default admin. */
    public static void initialize() {
        String users = """
                CREATE TABLE IF NOT EXISTS users (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    full_name     TEXT NOT NULL,
                    username      TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL,
                    role          TEXT NOT NULL CHECK (role IN ('CUSTOMER','BAKER','ADMIN')),
                    status        TEXT NOT NULL DEFAULT 'PENDING'
                                  CHECK (status IN ('PENDING','APPROVED','REJECTED')),
                    created_at    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""";

        String cakes = """
                CREATE TABLE IF NOT EXISTS cakes (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    name        TEXT NOT NULL,
                    description TEXT,
                    price       REAL NOT NULL CHECK (price >= 0)
                )""";

        String orders = """
                CREATE TABLE IF NOT EXISTS orders (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    customer_id INTEGER NOT NULL,
                    status      TEXT NOT NULL DEFAULT 'PLACED',
                    created_at  TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (customer_id) REFERENCES users(id)
                )""";

        String orderItems = """
                CREATE TABLE IF NOT EXISTS order_items (
                    id       INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id INTEGER NOT NULL,
                    cake_id  INTEGER NOT NULL,
                    quantity INTEGER NOT NULL CHECK (quantity > 0),
                    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
                    FOREIGN KEY (cake_id)  REFERENCES cakes(id)
                )""";

        // Audit trail: every critical action is recorded here.
        // user_id is nullable so system actions (registration, failed login) can be logged too.
        String activityLogs = """
                CREATE TABLE IF NOT EXISTS activity_logs (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id     INTEGER,
                    action_type TEXT NOT NULL,
                    description TEXT NOT NULL,
                    timestamp   TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
                )""";

        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(users);
            st.execute(cakes);
            st.execute(orders);
            st.execute(orderItems);
            st.execute(activityLogs);
            seedCakes(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Database initialization failed: " + e.getMessage(), e);
        }

        new UserDao().ensureDefaultAdmin();
        System.out.println("[DB] Database initialized (foreign keys ON).");
    }

    /** Adds a few sample cakes the first time, so customers have something to order. */
    private static void seedCakes(Connection conn) throws SQLException {
        boolean empty;
        try (Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM cakes")) {
            empty = rs.next() && rs.getInt(1) == 0;
        }
        if (empty) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("""
                        INSERT INTO cakes (name, description, price) VALUES
                        ('Chocolate Cake', 'Rich chocolate with ganache', 550.00),
                        ('Ube Cake', 'Soft ube sponge with cream', 480.00),
                        ('Mocha Cake', 'Coffee-flavored layered cake', 520.00)""");
            }
        }
    }
}