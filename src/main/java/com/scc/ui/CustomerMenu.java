package com.scc.ui;

import com.scc.dao.ActivityLogDao;
import com.scc.dao.OrderDao;
import com.scc.model.Cake;
import com.scc.model.User;

import java.util.List;
import java.util.Optional;
import java.util.Scanner;

public class CustomerMenu {

    private final OrderDao orderDao;
    private final ActivityLogDao logDao;
    private final Scanner scanner;
    private final User user;

    public CustomerMenu(OrderDao orderDao, ActivityLogDao logDao, Scanner scanner, User user) {
        this.orderDao = orderDao;
        this.logDao = logDao;
        this.scanner = scanner;
        this.user = user;
    }

    public void show() {
        boolean running = true;
        while (running) {
            System.out.println("\n=== CUSTOMER MENU (" + user.username() + ") ===");
            System.out.println("1. Order a cake");
            System.out.println("0. Logout");
            System.out.print("Choose: ");
            switch (scanner.nextLine().trim()) {
                case "1" -> orderCake();
                case "0" -> running = false;
                default -> System.out.println("Invalid option.");
            }
        }
    }

    /** Domain-specific business transaction: every order is written to the audit log. */
    private void orderCake() {
        List<Cake> cakes = orderDao.findAllCakes();
        if (cakes.isEmpty()) {
            System.out.println("No cakes available right now.");
            return;
        }
        System.out.println("\n--- Cake Menu ---");
        for (Cake c : cakes) {
            System.out.printf("[%d] %-16s PHP %8.2f  - %s%n", c.id(), c.name(), c.price(), c.description());
        }

        int cakeId = readIntInput("Enter Cake ID (0 to cancel): ");
        if (cakeId == 0) {
            System.out.println("Order cancelled.");
            return;
        }
        Optional<Cake> cake = orderDao.findCakeById(cakeId);
        if (cake.isEmpty()) {
            System.out.println("No cake found with ID " + cakeId + ".");
            return;
        }

        int quantity = readIntInput("Quantity (0 to cancel): ");
        if (quantity == 0) {
            System.out.println("Order cancelled.");
            return;
        }
        if (quantity < 0) {
            System.out.println("Quantity must be at least 1.");
            return;
        }

        int orderId = orderDao.placeOrder(user.id(), cakeId, quantity);
        if (orderId > 0) {
            double total = cake.get().price() * quantity;
            System.out.printf("SUCCESS: Order #%d placed - %d x %s = PHP %.2f%n",
                    orderId, quantity, cake.get().name(), total);
            logDao.logAction(user.id(), "ORDER_PLACED",
                    String.format("Customer %d ordered %d x %s (Order #%d, PHP %.2f)",
                            user.id(), quantity, cake.get().name(), orderId, total));
        } else {
            System.out.println("Order failed. Please try again.");
        }
    }

    private int readIntInput(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a whole number (digits only).");
            }
        }
    }
}