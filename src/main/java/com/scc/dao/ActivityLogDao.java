package com.scc.dao;

import com.scc.model.ActivityLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** All SQL for the activity_logs table. Every query uses a PreparedStatement. */
public class ActivityLogDao {

    private static final String COLS = "id, user_id, action_type, description, timestamp";

    /** Records an action performed by a known user. */
    public boolean logAction(int userId, String actionType, String description) {
        return insert(userId, actionType, description);
    }

    /** Records an action that has no user yet (e.g. registration, failed login). */
    public boolean logSystemAction(String actionType, String description) {
        return insert(null, actionType, description);
    }

    /** Newest first. */
    public List<ActivityLog> getAllLogs() {
        String sql = "SELECT " + COLS + " FROM activity_logs ORDER BY timestamp DESC, id DESC";
        return query(sql, null);
    }

    /** Logs of one user, newest first. */
    public List<ActivityLog> getLogsByUserId(int userId) {
        String sql = "SELECT " + COLS + " FROM activity_logs WHERE user_id = ? "
                + "ORDER BY timestamp DESC, id DESC";
        return query(sql, userId);
    }

    /** Deletes every log entry. Returns the number deleted, or -1 on error. */
    public int clearAllLogs() {
        String sql = "DELETE FROM activity_logs";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB error] " + e.getMessage());
            return -1;
        }
    }

    // ---------- helpers ----------

    private boolean insert(Integer userId, String actionType, String description) {
        String sql = "INSERT INTO activity_logs (user_id, action_type, description) VALUES (?, ?, ?)";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (userId == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, userId);
            }
            ps.setString(2, actionType);
            ps.setString(3, description);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("[DB error] Could not write log: " + e.getMessage());
            return false;
        }
    }

    private List<ActivityLog> query(String sql, Integer userId) {
        List<ActivityLog> list = new ArrayList<>();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (userId != null) {
                ps.setInt(1, userId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int uid = rs.getInt("user_id");
                    Integer userIdValue = rs.wasNull() ? null : uid;
                    list.add(new ActivityLog(rs.getInt("id"), userIdValue,
                            rs.getString("action_type"), rs.getString("description"),
                            rs.getString("timestamp")));
                }
            }
        } catch (SQLException e) {
            System.out.println("[DB error] " + e.getMessage());
        }
        return list;
    }
}