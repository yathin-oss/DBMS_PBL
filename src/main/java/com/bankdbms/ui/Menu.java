package com.bankdbms.ui;

import com.bankdbms.exception.BankException;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A numbered console menu. Each action runs inside an error boundary: banking errors show their
 * (safe) message; anything unexpected is logged and reported generically, never as a stack trace.
 */
public class Menu {

    private static final Logger LOG = Logger.getLogger(Menu.class.getName());

    /** A menu action that may throw any runtime exception. */
    @FunctionalInterface
    public interface Action {
        void run();
    }

    private record Item(String label, Action action) {
    }

    private final String title;
    private final ConsoleIO io;
    private final String exitLabel;
    private final List<Item> items = new ArrayList<>();

    public Menu(String title, ConsoleIO io) {
        this(title, io, "Back");
    }

    public Menu(String title, ConsoleIO io, String exitLabel) {
        this.title = title;
        this.io = io;
        this.exitLabel = exitLabel;
    }

    public Menu add(String label, Action action) {
        items.add(new Item(label, action));
        return this;
    }

    /** Shows the menu until the operator chooses 0. */
    public void run() {
        while (true) {
            io.println();
            io.println("=== " + title + " ===");
            for (int i = 0; i < items.size(); i++) {
                io.println(String.format("%2d. %s", i + 1, items.get(i).label()));
            }
            io.println(" 0. " + exitLabel);
            String choice = io.line("Choose");
            if (choice.equals("0")) {
                return;
            }
            Item item = select(choice);
            if (item == null) {
                io.error("Please enter a number between 0 and " + items.size() + ".");
                continue;
            }
            perform(item.action());
        }
    }

    private Item select(String choice) {
        try {
            int index = Integer.parseInt(choice) - 1;
            return index >= 0 && index < items.size() ? items.get(index) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void perform(Action action) {
        try {
            action.run();
        } catch (ConsoleIO.InputClosedException e) {
            throw e;
        } catch (BankException e) {
            io.error(e.getMessage());
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unexpected error in console action", e);
            io.error("An unexpected error occurred. Details were written to logs/bank-dbms.log.");
        }
    }
}
