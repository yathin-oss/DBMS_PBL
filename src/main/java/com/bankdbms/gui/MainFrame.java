package com.bankdbms.gui;

import com.bankdbms.app.AppContext;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Main window: a sidebar of screens and a compact top bar around the screen area. The screens
 * are still the tabs of one tabbed pane (its tab strip is hidden and the sidebar selects tabs),
 * and each tab reloads its data from the database when selected.
 */
public class MainFrame extends JFrame {

    public MainFrame(AppContext app, String connectionInfo) {
        super("Bank Account and Transaction Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JTabbedPane tabs = createTabs(app);
        tabs.setUI(new HiddenTabsUI());
        tabs.setFocusable(false);
        tabs.addChangeListener(e -> refresh(tabs.getSelectedComponent()));

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.PAGE);
        main.add(topBar(tabs), BorderLayout.NORTH);
        main.add(tabs, BorderLayout.CENTER);
        getContentPane().add(new Sidebar(tabs, connectionInfo), BorderLayout.WEST);
        getContentPane().add(main, BorderLayout.CENTER);
        installShortcuts(tabs);

        setMinimumSize(new Dimension(1024, 680));
        setSize(1366, 860);
        setLocationRelativeTo(null);
        refresh(tabs.getSelectedComponent());
    }

    /** The application's screens, one tab each (also used by the automated GUI test). */
    public static JTabbedPane createTabs(AppContext app) {
        Theme.install();
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

    /** Breadcrumb of the current screen on the left, today's date on the right. */
    private static JComponent topBar(JTabbedPane tabs) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.CARD);
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                Theme.padding(Theme.SPACE_M + 2, Theme.SPACE_XL + 4)));
        JLabel crumb = new JLabel();
        crumb.setFont(Theme.BODY);
        Runnable update = () -> crumb.setText("<html><span style='color:#64748B'>Bank DBMS&nbsp;&nbsp;/&nbsp;&nbsp;</span><b>"
                + tabs.getTitleAt(Math.max(tabs.getSelectedIndex(), 0)).replace("&", "&amp;") + "</b></html>");
        update.run();
        tabs.addChangeListener(e -> update.run());
        crumb.setForeground(Theme.TEXT);
        JLabel date = Gui.muted(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)));
        bar.add(crumb, BorderLayout.WEST);
        bar.add(date, BorderLayout.EAST);
        return bar;
    }

    /** Cmd/Ctrl + 1..8 opens the matching screen. */
    private void installShortcuts(JTabbedPane tabs) {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        JComponent root = getRootPane();
        for (int i = 0; i < tabs.getTabCount() && i < 9; i++) {
            int index = i;
            String key = "screen" + i;
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, mask), key);
            root.getActionMap().put(key, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    tabs.setSelectedIndex(index);
                }
            });
        }
    }

    /** Tabbed pane UI without a tab strip or content border: navigation lives in the sidebar. */
    private static final class HiddenTabsUI extends BasicTabbedPaneUI {
        @Override
        protected int calculateTabAreaHeight(int placement, int runCount, int maxTabHeight) {
            return 0;
        }

        @Override
        protected int calculateTabAreaWidth(int placement, int runCount, int maxTabWidth) {
            return 0;
        }

        @Override
        protected Insets getContentBorderInsets(int placement) {
            return new Insets(0, 0, 0, 0);
        }

        @Override
        protected Insets getTabAreaInsets(int placement) {
            return new Insets(0, 0, 0, 0);
        }

        @Override
        protected void paintTabArea(Graphics g, int placement, int selectedIndex) {
        }

        @Override
        protected void paintContentBorder(Graphics g, int placement, int selectedIndex) {
        }
    }
}
