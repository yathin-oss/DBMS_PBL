package com.bankdbms.gui;

import com.bankdbms.report.ReportTable;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Table model over rows of values; built from a ReportTable or from any list via a row mapper. */
public class RowTableModel extends AbstractTableModel {

    private List<String> columns = List.of();
    private List<List<Object>> rows = List.of();

    public void setReport(ReportTable table) {
        set(table.columns().stream().map(RowTableModel::pretty).toList(), table.rows());
    }

    public <T> void setItems(List<String> headers, List<T> items, Function<T, List<Object>> mapper) {
        List<List<Object>> mapped = new ArrayList<>();
        for (T item : items) {
            mapped.add(mapper.apply(item));
        }
        set(headers, mapped);
    }

    public void set(List<String> headers, List<List<Object>> data) {
        boolean structureChanged = !headers.equals(columns);
        columns = List.copyOf(headers);
        rows = new ArrayList<>(data);
        if (structureChanged) {
            fireTableStructureChanged();
        } else {
            fireTableDataChanged();
        }
    }

    public Object value(int row, String header) {
        return rows.get(row).get(columns.indexOf(header));
    }

    public List<String> columns() {
        return columns;
    }

    public List<List<Object>> rows() {
        return rows;
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column);
    }

    @Override
    public Object getValueAt(int row, int column) {
        return rows.get(row).get(column);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        for (List<Object> row : rows) {
            Object v = row.get(column);
            if (v != null) {
                return v instanceof Comparable<?> ? v.getClass() : Object.class;
            }
        }
        return Object.class;
    }

    /** "outstanding_principal" -> "Outstanding principal". */
    static String pretty(String column) {
        String s = column.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
