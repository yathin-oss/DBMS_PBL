package com.bankdbms.ui;

import com.bankdbms.app.AppContext;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.model.Customer;
import com.bankdbms.model.CustomerForm;
import com.bankdbms.model.Gender;
import com.bankdbms.model.IdProofType;
import com.bankdbms.util.Validator;

import java.util.ArrayList;
import java.util.List;

/** Customer screens: register, view, update, search, list (FR-01 to FR-05). */
public class CustomerMenu {

    private final AppContext app;
    private final ConsoleIO io;

    public CustomerMenu(AppContext app, ConsoleIO io) {
        this.app = app;
        this.io = io;
    }

    public void show() {
        new Menu("Customers", io)
                .add("Register customer", this::register)
                .add("View customer (with accounts and loans)", this::view)
                .add("Update customer", this::update)
                .add("Search customers", this::search)
                .add("List all customers", () -> printCustomers(app.customers.list()))
                .run();
    }

    private void register() {
        Customer c = app.customers.register(readForm(null));
        io.success("Customer registered with id " + c.customerId() + ".");
    }

    private void view() {
        Customer c = app.customers.get(io.integer("Customer id"));
        io.println("  " + c.fullName() + "  (id " + c.customerId() + ")");
        io.println("  DOB " + c.dateOfBirth() + " | " + c.gender() + " | " + c.email() + " | " + c.phone());
        io.println("  " + c.address() + ", " + c.city());
        io.println("  ID proof: " + c.idProofType() + " " + Validator.mask(c.idProofNumber()));
        TablePrinter.print(io, app.reports.accountsOfCustomer(c.customerId()));
        int loans = app.loans.positionsForCustomer(c.customerId()).size();
        if (loans > 0) {
            io.println("  Loans: " + loans + " (see Loans menu)");
        }
    }

    private void update() {
        Customer existing = app.customers.get(io.integer("Customer id"));
        io.println("  Press Enter to keep the current value.");
        Customer updated = app.customers.update(existing.customerId(), readForm(existing));
        io.success("Customer " + updated.customerId() + " updated.");
    }

    private void search() {
        printCustomers(app.customers.search(io.required("Name, email, phone or ID number contains")));
    }

    /** Reads customer details; when updating, a blank answer keeps the current value. */
    private CustomerForm readForm(Customer current) {
        boolean updating = current != null;
        return new CustomerForm(
                ask("First name", updating ? current.firstName() : null),
                ask("Last name", updating ? current.lastName() : null),
                io.date("Date of birth", updating ? current.dateOfBirth() : null),
                askEnum("Gender", Gender.class, updating ? current.gender() : null),
                ask("Email", updating ? current.email() : null),
                ask("Phone", updating ? current.phone() : null),
                ask("Address", updating ? current.address() : null),
                ask("City", updating ? current.city() : null),
                askEnum("ID proof type", IdProofType.class, updating ? current.idProofType() : null),
                askSecret("ID proof number", updating ? current.idProofNumber() : null));
    }

    private String ask(String label, String current) {
        if (current == null) {
            return io.line(label);
        }
        String value = io.line(label + " [" + current + "]");
        return value.isEmpty() ? current : value;
    }

    /** Like {@link #ask} but shows only the last four characters of the current value. */
    private String askSecret(String label, String current) {
        if (current == null) {
            return io.line(label);
        }
        String value = io.line(label + " [" + Validator.mask(current) + "]");
        return value.isEmpty() ? current : value;
    }

    private <E extends Enum<E>> E askEnum(String label, Class<E> type, E current) {
        if (current == null) {
            return io.choice(label, type);
        }
        String value = io.line(label + " [" + current + "]");
        if (value.isEmpty()) {
            return current;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("'" + value + "' is not a valid " + label + ".");
        }
    }

    private void printCustomers(List<Customer> customers) {
        List<List<Object>> rows = new ArrayList<>();
        for (Customer c : customers) {
            rows.add(List.of(c.customerId(), c.fullName(), c.email(), c.phone(), c.city(),
                    c.idProofType() + " " + Validator.mask(c.idProofNumber())));
        }
        TablePrinter.print(io, List.of("id", "name", "email", "phone", "city", "id_proof"), rows);
    }
}
