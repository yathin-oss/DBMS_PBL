package com.bankdbms.ui;

import com.bankdbms.exception.ValidationException;
import com.bankdbms.util.Money;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Console input/output helpers. Parsing errors become {@link ValidationException}s with clear messages. */
public class ConsoleIO {

    /** Thrown when standard input is closed (end of a scripted session). */
    public static class InputClosedException extends RuntimeException {
        InputClosedException() {
            super("Input closed");
        }
    }

    private final BufferedReader in;
    private final PrintStream out;

    public ConsoleIO() {
        this(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)), System.out);
    }

    public ConsoleIO(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public void println(String text) {
        out.println(text);
    }

    public void println() {
        out.println();
    }

    public void success(String text) {
        out.println("  OK: " + text);
    }

    public void error(String text) {
        out.println("  ERROR: " + text);
    }

    public String line(String prompt) {
        out.print(prompt + ": ");
        out.flush();
        try {
            String value = in.readLine();
            if (value == null) {
                throw new InputClosedException();
            }
            return value.strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String required(String prompt) {
        String value = line(prompt);
        if (value.isEmpty()) {
            throw new ValidationException(prompt + " is required.");
        }
        return value;
    }

    public int integer(String prompt) {
        String value = required(prompt);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ValidationException("'" + value + "' is not a whole number.");
        }
    }

    public Integer optionalInteger(String prompt) {
        String value = line(prompt + " (blank = none)");
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ValidationException("'" + value + "' is not a whole number.");
        }
    }

    public BigDecimal money(String prompt) {
        return Money.parse(required(prompt));
    }

    public BigDecimal optionalMoney(String prompt) {
        String value = line(prompt + " (blank = default)");
        return value.isEmpty() ? null : Money.parse(value);
    }

    public LocalDate date(String prompt, LocalDate defaultValue) {
        String value = line(prompt + " [yyyy-mm-dd" + (defaultValue == null ? "" : ", blank = " + defaultValue) + "]");
        if (value.isEmpty() && defaultValue != null) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException("'" + value + "' is not a date in the form yyyy-mm-dd.");
        }
    }

    public <E extends Enum<E>> E choice(String prompt, Class<E> type) {
        String options = Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining("/"));
        String value = required(prompt + " (" + options + ")").toUpperCase();
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("'" + value + "' is not one of " + options + ".");
        }
    }

    public boolean confirm(String prompt) {
        return line(prompt + " (y/n)").equalsIgnoreCase("y");
    }
}
