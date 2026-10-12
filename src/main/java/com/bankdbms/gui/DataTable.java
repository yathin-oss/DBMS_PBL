package com.bankdbms.gui;

import com.bankdbms.ui.TablePrinter;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Set;

/**
 * A read-only, sortable table that formats money with two decimals (right-aligned), dates and
 * timestamps, shows status words (OVERDUE, BLOCKED, ...) as coloured badges, and explains itself
 * when it has no rows.
 */
public class DataTable extends JTable {

    private static final int CELL_PADDING = 12;

    private String emptyText = "No records to display.";

    public DataTable(TableModel model) {
        super(model);
        setAutoCreateRowSorter(true);
        setFillsViewportHeight(true);
        setRowHeight(34);
        setFont(Theme.BODY);
        setShowVerticalLines(false);
        setShowHorizontalLines(true);
        setGridColor(Theme.HOVER);
        setIntercellSpacing(new Dimension(0, 1));
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setBackground(Theme.CARD);
        JTableHeader header = getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 36));
    }

    /** Message painted in the middle of the table while it has no rows. */
    public DataTable emptyText(String text) {
        this.emptyText = text;
        repaint();
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getRowCount() == 0 && emptyText != null) {
            Graphics2D g2 = Theme.smooth(g);
            g2.setFont(Theme.BODY);
            g2.setColor(Theme.TEXT_MUTED);
            FontMetrics fm = g2.getFontMetrics();
            int w = getVisibleRect().width;
            int y = getVisibleRect().y + Math.min(64, Math.max(fm.getAscent() + 8, getVisibleRect().height / 2));
            g2.drawString(emptyText, getVisibleRect().x + (w - fm.stringWidth(emptyText)) / 2, y);
            g2.dispose();
        }
    }

    /**
     * Columns keep their content width; the table fills the card when it fits and scrolls
     * horizontally when it does not, so values are never cut short in a narrow window.
     */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getParent() == null || getPreferredSize().width <= getParent().getWidth();
    }

    /** After JTable has rebuilt its columns for new data, size them to the content. */
    @Override
    public void tableChanged(TableModelEvent e) {
        super.tableChanged(e);
        if (getColumnModel() != null && getTableHeader() != null) {
            fitColumnsToContent();
            // A cleared detail table has no columns: collapse the header so only the empty-state message shows.
            JTableHeader header = getTableHeader();
            header.setPreferredSize(new Dimension(header.getPreferredSize().width, getColumnCount() == 0 ? 0 : 36));
            header.revalidate();
        }
    }

    /** Every column uses the formatting renderer (JTable would otherwise pick its own for numbers and dates). */
    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        return RENDERER;
    }

    /** Sizes columns to their header and content so account and loan numbers are not cut off. */
    public void fitColumnsToContent() {
        TableColumnModel columns = getColumnModel();
        int rows = Math.min(getRowCount(), 200);
        for (int c = 0; c < columns.getColumnCount(); c++) {
            TableColumn column = columns.getColumn(c);
            Component header = getTableHeader().getDefaultRenderer()
                    .getTableCellRendererComponent(this, column.getHeaderValue(), false, false, -1, c);
            int width = header.getPreferredSize().width;
            for (int r = 0; r < rows; r++) {
                Component cell = prepareRenderer(getCellRenderer(r, c), r, c);
                width = Math.max(width, cell.getPreferredSize().width);
            }
            column.setPreferredWidth(Math.min(width + 8, 440));
        }
    }

    /** Model row of the current selection, or -1. */
    public int selectedModelRow() {
        int view = getSelectedRow();
        return view < 0 ? -1 : convertRowIndexToModel(view);
    }

    /** True if the column holds numbers (judged from the first non-empty value). */
    private boolean numericColumn(int viewColumn) {
        if (viewColumn < 0 || viewColumn >= getColumnCount()) {
            return false;
        }
        return Number.class.isAssignableFrom(getModel().getColumnClass(convertColumnIndexToModel(viewColumn)));
    }

    private static final Renderer RENDERER = new Renderer();

    private enum Badge {
        SUCCESS(Theme.SUCCESS, new Color(0xDCFCE7)),
        WARNING(Theme.WARNING, new Color(0xFEF3C7)),
        ERROR(Theme.ERROR, new Color(0xFEE2E2)),
        NEUTRAL(new Color(0x475569), new Color(0xF1F5F9));

        final Color fg;
        final Color bg;

        Badge(Color fg, Color bg) {
            this.fg = fg;
            this.bg = bg;
        }

        static Badge of(Object value) {
            if (!(value instanceof String s)) {
                return null;
            }
            if (Set.of("ACTIVE", "PAID", "PASS", "ON_TIME", "POSTED").contains(s)) {
                return SUCCESS;
            }
            if (Set.of("FROZEN", "LATE", "PENDING").contains(s)) {
                return WARNING;
            }
            if (Set.of("OVERDUE", "BLOCKED", "FAIL").contains(s)) {
                return ERROR;
            }
            if (Set.of("CLOSED", "INACTIVE", "REVERSED").contains(s)) {
                return NEUTRAL;
            }
            return null;
        }
    }

    private static final class Renderer extends DefaultTableCellRenderer {
        private Badge badge;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, TablePrinter.format(value), selected, false, row, column);
            badge = Badge.of(value);
            setOpaque(true);
            setFont(Theme.BODY);
            setHorizontalAlignment(value instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
            int extra = badge == null ? 0 : 9;
            setBorder(BorderFactory.createEmptyBorder(0, CELL_PADDING + extra, 0, CELL_PADDING + extra));
            if (selected) {
                setBackground(Theme.SELECTION);
                setForeground(Theme.TEXT);
            } else {
                setBackground(row % 2 == 0 ? Theme.CARD : Theme.SUBTLE);
                setForeground(Theme.TEXT);
            }
            if (badge != null) {
                setForeground(badge.fg);
                setFont(Theme.LABEL);
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (badge != null) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                Graphics2D g2 = Theme.smooth(g);
                FontMetrics fm = g2.getFontMetrics(getFont());
                int w = fm.stringWidth(getText()) + 18;
                int h = fm.getHeight() + 4;
                g2.setColor(badge.bg);
                g2.fillRoundRect(CELL_PADDING, (getHeight() - h) / 2, w, h, h, h);
                g2.dispose();
                setOpaque(false);       // background and pill are painted above; let the label draw text only
                super.paintComponent(g);
                setOpaque(true);
            } else {
                super.paintComponent(g);
            }
        }
    }

    /** Column header: muted bold label on a light strip, numeric columns right-aligned, sort direction shown. */
    private final class HeaderRenderer extends JLabel implements TableCellRenderer {
        HeaderRenderer() {
            setOpaque(true);
            setFont(Theme.LABEL);
            setForeground(Theme.TEXT_MUTED);
            setBackground(Theme.SUBTLE);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            String text = value == null ? "" : value.toString();
            String arrow = "";
            RowSorter<?> sorter = table.getRowSorter();
            if (sorter != null && column >= 0) {
                List<? extends RowSorter.SortKey> keys = sorter.getSortKeys();
                if (!keys.isEmpty() && keys.get(0).getColumn() == table.convertColumnIndexToModel(column)
                        && keys.get(0).getSortOrder() != SortOrder.UNSORTED) {
                    arrow = "  " + (keys.get(0).getSortOrder() == SortOrder.ASCENDING
                            ? Theme.symbol("\u25B2", "(asc)") : Theme.symbol("\u25BC", "(desc)"));
                }
            }
            setText(text + arrow);
            setHorizontalAlignment(numericColumn(column) ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(0, CELL_PADDING, 0, CELL_PADDING)));
            return this;
        }
    }
}
