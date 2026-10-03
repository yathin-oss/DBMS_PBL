package com.bankdbms.ui;

import com.bankdbms.report.ReportTable;
import com.bankdbms.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Renders rows as an aligned text table (money right-aligned with two decimals). */
public final class TablePrinter {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_WIDTH = 40;

    private TablePrinter() {
    }

    public static void print(ConsoleIO io, ReportTable table) {
        io.println();
        io.println("== " + table.title() + " (" + table.size() + " row" + (table.size() == 1 ? "" : "s") + ") ==");
        print(io, table.columns(), table.rows());
    }

    public static void print(ConsoleIO io, List<String> headers, List<List<Object>> rows) {
        List<List<String>> cells = new ArrayList<>();
        int[] widths = new int[headers.size()];
        for (int c = 0; c < headers.size(); c++) {
            widths[c] = headers.get(c).length();
        }
        boolean[] numeric = new boolean[headers.size()];
        for (List<Object> row : rows) {
            List<String> rendered = new ArrayList<>();
            for (int c = 0; c < headers.size(); c++) {
                Object value = row.get(c);
                numeric[c] |= value instanceof Number;
                String text = format(value);
                widths[c] = Math.min(MAX_WIDTH, Math.max(widths[c], text.length()));
                rendered.add(text);
            }
            cells.add(rendered);
        }
        io.println(line(headers, widths, new boolean[headers.size()]));
        StringBuilder rule = new StringBuilder();
        for (int w : widths) {
            rule.append("-".repeat(w)).append("  ");
        }
        io.println(rule.toString().stripTrailing());
        for (List<String> row : cells) {
            io.println(line(row, widths, numeric));
        }
        if (rows.isEmpty()) {
            io.println("(no rows)");
        }
    }

    public static String format(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal amount) {
            return amount.scale() == 2 ? Money.format(amount) : amount.toPlainString();
        }
        if (value instanceof LocalDateTime ts) {
            return ts.format(TIMESTAMP);
        }
        return value.toString();
    }

    private static String line(List<String> values, int[] widths, boolean[] rightAlign) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < values.size(); c++) {
            String v = values.get(c);
            if (v.length() > widths[c]) {
                v = v.substring(0, widths[c] - 1) + "~";
            }
            sb.append(rightAlign[c] ? String.format("%" + widths[c] + "s", v) : String.format("%-" + widths[c] + "s", v));
            sb.append("  ");
        }
        return sb.toString().stripTrailing();
    }
}
