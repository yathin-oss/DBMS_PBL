package com.bankdbms.gui;

import com.bankdbms.app.AppContext;
import com.bankdbms.ledger.PostingFaultInjector;
import com.bankdbms.support.TestDatabase;
import com.bankdbms.util.BankClock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Builds every desktop screen without a display (headless) against the sample data, loads each
 * screen's data exactly as selecting its tab does, and checks that the tables are populated. Any
 * error dialog would throw HeadlessException and fail the test. Each screen is also rendered to
 * target/gui-snapshots/*.png for visual review.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GuiSmokeTest {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private JTabbedPane tabs;
    private AppContext app;

    @BeforeAll
    void build() throws Exception {
        TestDatabase.rebuild("sql/05_sample_data.sql");
        app = new AppContext(TestDatabase.config(), BankClock.system(), PostingFaultInjector.NONE);
        SwingUtilities.invokeAndWait(() -> tabs = MainFrame.createTabs(app));
    }

    @Test
    @DisplayName("TC-GUI-01 all eight screens load their data from the database without errors")
    void everyScreenLoads() throws Exception {
        assertEquals(8, tabs.getTabCount());
        File dir = new File("target/gui-snapshots");
        dir.mkdirs();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            int index = i;
            SwingUtilities.invokeAndWait(() -> {
                tabs.setSelectedIndex(index);
                ((Refreshable) tabs.getComponentAt(index)).refresh();
            });
            render(tabs, new File(dir, String.format("%d_%s.png", i + 1,
                    tabs.getTitleAt(i).toLowerCase().replaceAll("[^a-z]+", "_"))));
        }
    }

    @Test
    @DisplayName("TC-GUI-02 data screens show the sample data (customers, accounts, loans, branches)")
    void tablesArePopulated() throws Exception {
        int[] expectedMinimumRows = {4, 10, 11, 0, 0, 3, 4, 0};   // per tab, first table
        for (int i = 0; i < tabs.getTabCount(); i++) {
            int index = i;
            SwingUtilities.invokeAndWait(() -> ((Refreshable) tabs.getComponentAt(index)).refresh());
            List<JTable> tables = find((Container) tabs.getComponentAt(i), JTable.class);
            assertTrue(tables.get(0).getRowCount() >= expectedMinimumRows[i],
                    tabs.getTitleAt(i) + " shows " + tables.get(0).getRowCount() + " rows");
        }
    }

    @Test
    @DisplayName("TC-GUI-03 drop-downs are filled from the database (accounts, charge types)")
    void pickersArePopulated() throws Exception {
        SwingUtilities.invokeAndWait(() -> ((Refreshable) tabs.getComponentAt(3)).refresh());
        List<JComboBox> combos = find((Container) tabs.getComponentAt(3), JComboBox.class);
        assertTrue(combos.stream().anyMatch(c -> c.getItemCount() >= 10), "an account picker lists the sample accounts");
        assertTrue(combos.stream().anyMatch(c -> c.getItemCount() == 4), "the charge-type picker lists 4 charge types");
    }

    @Test
    @DisplayName("TC-GUI-04 posting a deposit from the Transactions screen updates the database and shows the ledger lines")
    void depositThroughTheScreen() throws Exception {
        Container screen = (Container) tabs.getComponentAt(3);
        SwingUtilities.invokeAndWait(() -> {
            tabs.setSelectedIndex(3);
            ((Refreshable) screen).refresh();
        });
        JComboBox<?> account = find(screen, JComboBox.class).get(0);
        String accountNumber = ((Choice) account.getItemAt(1)).key();      // second account in the list (ACTIVE)
        java.math.BigDecimal before = app.accounts.getByNumber(accountNumber).balance();
        SwingUtilities.invokeAndWait(() -> {
            account.setSelectedIndex(1);
            List<JTextField> fields = find(screen, JTextField.class);
            fields.get(0).setText("1234.50");                             // Deposit tab: amount
            fields.get(1).setText("GUI test deposit");                    // Deposit tab: narration
            button(screen, "Post deposit").doClick();
        });
        assertEquals(before.add(new java.math.BigDecimal("1234.50")), app.accounts.getByNumber(accountNumber).balance());
        JTable result = find(screen, JTable.class).get(0);
        assertEquals(2, result.getRowCount(), "the two double-entry lines are displayed");
        render(tabs, new File("target/gui-snapshots/4b_transactions_after_deposit.png"));
    }

    @Test
    @DisplayName("TC-GUI-05 running an account statement from the Reports screen shows reconciled lines")
    void statementThroughTheScreen() throws Exception {
        Container screen = (Container) tabs.getComponentAt(7);
        SwingUtilities.invokeAndWait(() -> {
            tabs.setSelectedIndex(7);
            ((Refreshable) screen).refresh();
            List<JComboBox> combos = find(screen, JComboBox.class);
            JComboBox<?> account = combos.get(1);
            for (int i = 0; i < account.getItemCount(); i++) {
                if (account.getItemAt(i).toString().contains("Ananya")) {
                    account.setSelectedIndex(i);
                }
            }
            List<JTextField> fields = find(screen, JTextField.class);
            fields.get(0).setText("2026-08-01");
            fields.get(1).setText("2026-09-30");
            button(screen, "Run report").doClick();
        });
        JTable result = find(screen, JTable.class).get(0);
        assertEquals(6, result.getRowCount());
        boolean reconciled = find(screen, javax.swing.JLabel.class).stream()
                .anyMatch(l -> l.getText() != null && l.getText().contains("opening + credits - debits = closing"));
        assertTrue(reconciled, "statement footer shows the reconciliation check");
        render(tabs, new File("target/gui-snapshots/8b_statement.png"));
    }

    private static JButton button(Container root, String text) {
        return find(root, JButton.class).stream().filter(b -> text.equals(b.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("No button " + text));
    }

    private static void render(Component root, File file) throws Exception {
        BufferedImage[] image = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            attachTableHeaders((Container) root);
            root.setSize(new Dimension(1280, 800));
            layoutAll(root);
            image[0] = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image[0].createGraphics();
            root.paint(g);
            g.dispose();
        });
        try {
            ImageIO.write(image[0], "png", file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Headless only: JTable puts its header into the enclosing scroll pane when it is displayed
     * (addNotify), which never happens off-screen, so do that step explicitly for the snapshot.
     */
    private static void attachTableHeaders(Container root) {
        for (JTable table : find(root, JTable.class)) {
            if (table.getParent() != null && table.getParent().getParent() instanceof JScrollPane pane) {
                pane.setColumnHeaderView(table.getTableHeader());
            }
        }
    }

    private static void layoutAll(Component c) {
        c.doLayout();
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                layoutAll(child);
            }
        }
    }

    private static <T> List<T> find(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                found.add(type.cast(c));
            }
            if (c instanceof Container child) {
                found.addAll(find(child, type));
            }
        }
        return found;
    }
}
