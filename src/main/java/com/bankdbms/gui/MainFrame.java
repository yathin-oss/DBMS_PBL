package com.bankdbms.gui;

import com.bankdbms.app.AppContext;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

/** Main window: one tab per area; each tab reloads its data from the database when selected. */
public class MainFrame extends JFrame {

    public MainFrame(AppContext app, String connectionInfo) {
        super("Bank Account and Transaction Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JTabbedPane tabs = createTabs(app);
        tabs.addChangeListener(e -> refresh(tabs.getSelectedComponent()));
        JLabel status = new JLabel(connectionInfo);
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        getContentPane().add(tabs, BorderLayout.CENTER);
        getContentPane().add(status, BorderLayout.SOUTH);
        setMinimumSize(new Dimension(1000, 650));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        refresh(tabs.getSelectedComponent());
    }

    /** The application's screens, one tab each (also used by the automated GUI test). */
    public static JTabbedPane createTabs(AppContext app) {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Dashboard", new DashboardPanel(app));
        tabs.addTab("Customers", new CustomersPanel(app));
        tabs.addTab("Accounts", new AccountsPanel(app));
        tabs.addTab("Transactions", new TransactionsPanel(app));
        tabs.addTab("Beneficiaries", new BeneficiariesPanel(app));
        tabs.addTab("Loans", new LoansPanel(app));
        tabs.addTab("Branches & products", new BranchesPanel(app));
        tabs.addTab("Statements & reports", new ReportsPanel(app));
        return tabs;
    }

    private static void refresh(Component tab) {
        if (tab instanceof Refreshable r) {
            r.refresh();
        }
    }
}
