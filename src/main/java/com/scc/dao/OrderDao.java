package com.scc.dao;

import com.scc.model.Cake;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Cakes and orders - the bakeshop's domain-specific business transaction. */
public class OrderDao {

    public List<Cake> findAllCakes() {
        List<Cake> list = new ArrayList<>();
        String sql = "SELECT id, name, description, price FROM cakes ORDER BY id";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            System.out.println("[DB error] " + e.getMessage());
        }
        return list;
    }

    public Optional<Cake> findCakeById(int cakeId) {
        String sql = "SELECT id, name, description, price FROM cakes WHERE id = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, cakeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            System.out.println("[DB error] " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Saves an order and its item in ONE transaction.
     * Returns the new order ID, or -1 if it failed.
     */
    public int placeOrder(int customerId, int cakeId, int quantity) {
        String orderSql = "INSERT INTO orders (customer_id) VALUES (?)";
        String itemSql = "INSERT INTO order_items (order_id, cake_id, quantity) VALUES (?, ?, ?)";
        try (Connection c = DatabaseConnection.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement orderPs = c.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement itemPs = c.prepareStatement(itemSql)) {
                orderPs.setInt(1, customerId);
                orderPs.executeUpdate();
                int orderId;
                try (ResultSet keys = orderPs.getGeneratedKeys()) {
                    if (!keys.next()) {
                        c.rollback();
                        return -1;
                    }
                    orderId = keys.getInt(1);
                }
                itemPs.setInt(1, orderId);
                itemPs.setInt(2, cakeId);
                itemPs.setInt(3, quantity);
                itemPs.executeUpdate();
                c.commit();
                return orderId;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.out.println("[DB error] " + e.getMessage());
            return -1;
        }
    }

    private Cake map(ResultSet rs) throws SQLException {
        return new Cake(rs.getInt("id"), rs.getString("name"),
                rs.getString("description"), rs.getDouble("price"));
    }
}