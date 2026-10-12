package com.bankdbms.gui;

import com.bankdbms.app.AppContext;
import com.bankdbms.app.Main;
import com.bankdbms.config.DbConfig;
import com.bankdbms.exception.BankException;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Desktop (Swing) entry point. Uses exactly the same services, ledger and database as the console;
 * only the presentation layer differs.
 */
public final class BankApp {

    private BankApp() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--console")) {
            Main.main(new String[0]);
            return;
        }
        Main.configureLogging();
        System.setProperty("awt.useSystemAAFontSettings", "on");    // smooth text on every platform
        SwingUtilities.invokeLater(BankApp::start);
    }

    private static void start() {
        Theme.install();
        try {
            DbConfig config = DbConfig.load();
            Main.checkConnection(config);
            AppContext app = new AppContext(config);
            new MainFrame(app, "Connected to database '" + config.databaseName() + "' as " + config.user()).setVisible(true);
        } catch (BankException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Cannot start", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
