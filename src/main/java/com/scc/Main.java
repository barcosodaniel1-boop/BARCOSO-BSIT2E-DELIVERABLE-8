package com.scc;

import com.scc.dao.ActivityLogDao;
import com.scc.dao.DatabaseInitializer;
import com.scc.dao.OrderDao;
import com.scc.dao.UserDao;
import com.scc.ui.Authentication;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        // 1. Initialize database (creates every table, including activity_logs)
        DatabaseInitializer.initialize();

        // 2. Instantiate shared DAOs (single instances)
        UserDao userDao = new UserDao();
        ActivityLogDao logDao = new ActivityLogDao();
        OrderDao orderDao = new OrderDao();

        // 3. Delegate to Authentication
        try (Scanner scanner = new Scanner(System.in)) {
            new Authentication(userDao, logDao, orderDao, scanner).start();
        }
    }
}