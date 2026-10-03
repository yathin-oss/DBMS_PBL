package com.bankdbms.gui;

import com.bankdbms.ui.TablePrinter;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.Color;
import java.awt.Component;

/**
 * A read-only, sortable table that formats money with two decimals (right-aligned), dates and
 * timestamps, and highlights status words (OVERDUE, BLOCKED, ...).
 */
public class DataTable extends JTable {

    public DataTable(TableModel model) {
        super(model);
        setAutoCreateRowSorter(true);
        setFillsViewportHeight(true);
        setRowHeight(getRowHeight() + 6);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        getTableHeader().setReorderingAllowed(false);
    }

    /** After JTable has rebuilt its columns for new data, size them to the content. */
    @Override
    public void tableChanged(TableModelEvent e) {
        super.tableChanged(e);
        if (getColumnModel() != null && getTableHeader() != null) {
            fitColumnsToContent();
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
            column.setPreferredWidth(Math.min(width + 16, 420));
        }
    }

    /** Model row of the current selection, or -1. */
    public int selectedModelRow() {
        int view = getSelectedRow();
        return view < 0 ? -1 : convertRowIndexToModel(view);
    }

    private static final Renderer RENDERER = new Renderer();

    private static final class Renderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, TablePrinter.format(value), selected, focus, row, column);
            setHorizontalAlignment(value instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
            if (!selected) {
                setForeground(colourFor(value));
                setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xF4F7FB));
            }
            return this;
        }

        private static Color colourFor(Object value) {
            if (value instanceof String s) {
                switch (s) {
                    case "OVERDUE": case "BLOCKED": case "FAIL": case "LATE": case "REVERSED": case "INACTIVE":
                        return Gui.ERROR_RED;
                    case "FROZEN": case "CLOSED":
                        return new Color(0xB26A00);
                    case "PAID": case "PASS": case "ON_TIME":
                        return Gui.OK_GREEN;
                    default:
                        return Color.BLACK;
                }
            }
            return Color.BLACK;
        }
    }
}
