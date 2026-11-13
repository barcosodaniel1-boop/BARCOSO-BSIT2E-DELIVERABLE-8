package com.scc.ui;

import com.scc.dao.ActivityLogDao;
import com.scc.dao.UserDao;
import com.scc.model.ActivityLog;
import com.scc.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;

public class AdminMenu {

    private static final Set<String> VALID_ROLES = Set.of("CUSTOMER", "BAKER", "ADMIN");
    private static final Set<String> VALID_STATUSES = Set.of("APPROVED", "PENDING");

    private final UserDao userDao;
    private final ActivityLogDao logDao;
    private final Scanner scanner;
    private final User admin;

    public AdminMenu(UserDao userDao, ActivityLogDao logDao, Scanner scanner, User admin) {
        this.userDao = userDao;
        this.logDao = logDao;
        this.scanner = scanner;
        this.admin = admin;
    }

    public void show() {
        boolean running = true;
        while (running) {
            System.out.println("\n=== ADMIN MENU (" + admin.username() + ") ===");
            System.out.println("1. Review pending accounts");
            System.out.println("2. Update user role and status");
            System.out.println("3. View System Audit Logs");
            System.out.println("0. Logout");
            System.out.print("Choose: ");
            switch (scanner.nextLine().trim()) {
                case "1" -> reviewPending();
                case "2" -> updateUser();
                case "3" -> auditLogs();
                case "0" -> running = false;
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private void reviewPending() {
        List<User> pending = userDao.findByStatus("PENDING");
        if (pending.isEmpty()) {
            System.out.println("No pending accounts.");
            return;
        }
        for (User u : pending) {
            System.out.printf("[%d] %s (%s) - %s%n", u.id(), u.fullName(), u.username(), u.role());
        }
        System.out.print("Enter user ID to review (blank to cancel): ");
        String idText = scanner.nextLine().trim();
        if (idText.isEmpty()) {
            return;
        }
        try {
            int id = Integer.parseInt(idText);
            System.out.print("(A)pprove or (R)eject? ");
            String action = scanner.nextLine().trim().toUpperCase();
            if (action.equals("A")) {
                if (userDao.updateStatus(id, "APPROVED")) {
                    System.out.println("Approved.");
                    logDao.logAction(admin.id(), "USER_APPROVED",
                            "Admin " + admin.id() + " approved account for User ID " + id);
                } else {
                    System.out.println("Update failed.");
                }
            } else if (action.equals("R")) {
                if (userDao.updateStatus(id, "REJECTED")) {
                    System.out.println("Rejected.");
                    logDao.logAction(admin.id(), "USER_REJECTED",
                            "Admin " + admin.id() + " rejected account for User ID " + id);
                } else {
                    System.out.println("Update failed.");
                }
            } else {
                System.out.println("Cancelled.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid ID.");
        }
    }

    // ---------- Update user role & status ----------

    private void updateUser() {
        System.out.println("\n--- Update User Role and Status ---");
        printUserTable(userDao.findAll());

        int targetUserId = readIntInput("Enter User ID to update (0 to cancel): ");
        if (targetUserId == 0) {
            System.out.println("Update cancelled.");
            return;
        }

        // Security rule: an admin cannot modify their own active account.
        if (targetUserId == admin.id()) {
            System.out.println("WARNING: Modifying the currently active admin account is restricted.");
            return;
        }

        Optional<User> target = userDao.findById(targetUserId);
        if (target.isEmpty()) {
            System.out.println("No user found with ID " + targetUserId + ".");
            return;
        }
        System.out.printf("Selected: %s (%s) - current role: %s, status: %s%n",
                target.get().fullName(), target.get().username(),
                target.get().role(), target.get().status());

        String newRole = readValidRole();
        if (newRole == null) {
            System.out.println("Update cancelled.");
            return;
        }

        String newStatus = readValidStatus();
        if (newStatus == null) {
            System.out.println("Update cancelled.");
            return;
        }

        if (userDao.updateUserRoleAndStatus(targetUserId, newRole, newStatus)) {
            System.out.printf("SUCCESS: User ID %d updated -> role: %s, status: %s%n",
                    targetUserId, newRole, newStatus);
            logDao.logAction(admin.id(), "USER_UPDATED",
                    "Admin " + admin.id() + " changed User " + targetUserId
                            + " (role " + target.get().role() + " -> " + newRole
                            + ", status " + target.get().status() + " -> " + newStatus + ")");
        } else {
            System.out.println("Update failed. Please try again.");
        }
    }

    private void printUserTable(List<User> users) {
        String line = "+------+----------------------+------------------+----------+----------+";
        System.out.println(line);
        System.out.printf("| %-4s | %-20s | %-16s | %-8s | %-8s |%n",
                "ID", "Full Name", "Username", "Role", "Status");
        System.out.println(line);
        for (User u : users) {
            System.out.printf("| %-4d | %-20s | %-16s | %-8s | %-8s |%n",
                    u.id(), truncate(u.fullName(), 20), truncate(u.username(), 16),
                    u.role(), u.status());
        }
        System.out.println(line);
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "~";
    }

    // ---------- System audit logs ----------

    private void auditLogs() {
        boolean running = true;
        while (running) {
            System.out.println("\n=== VIEW SYSTEM AUDIT LOGS ===");
            System.out.println("1. View All System Logs");
            System.out.println("2. Filter Logs by User ID");
            System.out.println("3. Clear All Logs");
            System.out.println("0. Back");
            System.out.print("Choose: ");
            switch (scanner.nextLine().trim()) {
                case "1" -> printLogTable(logDao.getAllLogs(), "ALL SYSTEM LOGS");
                case "2" -> {
                    int userId = readIntInput("Enter User ID to filter (0 to cancel): ");
                    if (userId != 0) {
                        printLogTable(logDao.getLogsByUserId(userId), "LOGS OF USER " + userId);
                    }
                }
                case "3" -> clearLogs();
                case "0" -> running = false;
                default -> System.out.println("Invalid option.");
            }
        }
    }

    /** Safe clear: the admin must type YES, and the clearing itself is logged afterwards. */
    private void clearLogs() {
        System.out.println("\nWARNING: This permanently deletes ALL audit log entries.");
        System.out.print("Type YES to confirm (anything else cancels): ");
        if (!scanner.nextLine().trim().equals("YES")) {
            System.out.println("Cancelled. No logs were deleted.");
            return;
        }
        int deleted = logDao.clearAllLogs();
        if (deleted >= 0) {
            System.out.println(deleted + " log entr" + (deleted == 1 ? "y" : "ies") + " cleared.");
            logDao.logAction(admin.id(), "LOGS_CLEARED",
                    "Admin " + admin.id() + " cleared " + deleted + " audit log entries.");
        } else {
            System.out.println("Unable to clear logs.");
        }
    }

    private void printLogTable(List<ActivityLog> logs, String title) {
        System.out.println("\n=== " + title + " ===");
        if (logs.isEmpty()) {
            System.out.println("No log records found.");
            return;
        }
        String format = "%-5s | %-8s | %-18s | %-35s | %-20s%n";
        String line = "-".repeat(98);
        System.out.println(line);
        System.out.printf(format, "ID", "User ID", "Action Type", "Description", "Timestamp");
        System.out.println(line);
        for (ActivityLog l : logs) {
            List<String> desc = wrap(l.description(), 35);
            System.out.printf(format, l.id(), l.userId() == null ? "N/A" : l.userId(),
                    truncate(l.actionType(), 18), desc.get(0),
                    l.timestamp() == null ? "-" : l.timestamp());
            for (int i = 1; i < desc.size(); i++) {
                System.out.printf(format, "", "", "", desc.get(i), "");
            }
        }
        System.out.println(line);
        System.out.println("Total logs: " + logs.size());
    }

    /** Splits long text into lines of at most `width` characters so the table never breaks. */
    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("-");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            while (word.length() > width) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (current.length() > 0 && current.length() + 1 + word.length() > width) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    // ---------- Input validation wrappers ----------

    /** Keeps asking until the user types a whole number. Never throws on bad input. */
    private int readIntInput(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a whole number (digits only).");
            }
        }
    }

    /** Keeps asking until a valid role is typed. Returns null if the user enters 0 (cancel). */
    private String readValidRole() {
        while (true) {
            System.out.print("Enter new role " + VALID_ROLES_TEXT_ROLES + " (0 to cancel): ");
            String input = scanner.nextLine().trim().toUpperCase();
            if (input.equals("0")) {
                return null;
            }
            if (VALID_ROLES.contains(input)) {
                return input;
            }
            System.out.println("Invalid role. Valid roles are: " + VALID_ROLES_TEXT_ROLES);
        }
    }

    /** Keeps asking until a valid status is typed. Returns null if the user enters 0 (cancel). */
    private String readValidStatus() {
        while (true) {
            System.out.print("Enter new status " + VALID_ROLES_TEXT_STATUS + " (0 to cancel): ");
            String input = scanner.nextLine().trim().toUpperCase();
            if (input.equals("0")) {
                return null;
            }
            if (VALID_STATUSES.contains(input)) {
                return input;
            }
            System.out.println("Invalid status. Valid statuses are: " + VALID_ROLES_TEXT_STATUS);
        }
    }

    private static final String VALID_ROLES_TEXT_ROLES = "(CUSTOMER / BAKER / ADMIN)";
    private static final String VALID_ROLES_TEXT_STATUS = "(APPROVED / PENDING)";
}