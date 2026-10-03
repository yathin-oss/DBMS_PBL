package com.bankdbms.report;

import java.util.List;

/** A tabular report: column labels plus rows of values, exactly as returned by the SQL query. */
public record ReportTable(String title, List<String> columns, List<List<Object>> rows) {

    public ReportTable {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }

    public int size() {
        return rows.size();
    }

    /** Value of a named column in a given row (used by tests and by the console printer). */
    public Object value(int row, String column) {
        int index = columns.indexOf(column);
        if (index < 0) {
            throw new IllegalArgumentException("No column '" + column + "' in report " + title);
        }
        return rows.get(row).get(index);
    }
}
