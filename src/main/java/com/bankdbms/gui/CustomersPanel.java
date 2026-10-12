package com.bankdbms.gui;

import com.bankdbms.app.AppContext;
import com.bankdbms.model.Customer;
import com.bankdbms.model.CustomerForm;
import com.bankdbms.model.Gender;
import com.bankdbms.model.IdProofType;
import com.bankdbms.util.Validator;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.util.Arrays;
import java.util.List;

/** Customer management (FR-01 to FR-05): list, search, register, edit, accounts of a customer. */
public class CustomersPanel extends JPanel implements Refreshable {

    private static final List<String> HEADERS = List.of("Id", "Name", "Date of birth", "Email", "Phone", "City", "ID proof");

    private final AppContext app;
    private final JTextField search = new HintTextField("Search by name, email, phone or ID", 24);
    private final RowTableModel model = new RowTableModel();
    private final DataTable table = new DataTable(model);
    private final RowTableModel accountsModel = new RowTableModel();

    public CustomersPanel(AppContext app) {
        super(new BorderLayout());
        this.app = app;
        Gui.page(this);
        search.addActionListener(e -> refresh());
        add(Gui.pageHeader("Customers", "Register, search and update customer records",
                Gui.toolbar(search, Gui.button("Search", this::refresh), Gui.button("Show all", () -> {
                    search.setText("");
                    refresh();
                }), Gui.primary("Register customer...", this::register), Gui.button("Edit selected...", this::edit))),
                BorderLayout.NORTH);
        table.emptyText("No customers match the search.");
        DataTable accounts = new DataTable(accountsModel).emptyText("Select a customer above to see their accounts.");
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, Gui.card("All customers", Gui.scroll(table)),
                Gui.card("Accounts of the selected customer", Gui.scroll(accounts)));
        split.setResizeWeight(0.65);
        add(split, BorderLayout.CENTER);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showAccounts();
            }
        });
    }

    @Override
    public void refresh() {
        Gui.attempt(this, () -> {
            String kw = Gui.text(search);
            List<Customer> customers = kw.isEmpty() ? app.customers.list() : app.customers.search(kw);
            model.setItems(HEADERS, customers, c -> Arrays.asList(c.customerId(), c.fullName(), c.dateOfBirth(),
                    c.email(), c.phone(), c.city(), c.idProofType() + " " + Validator.mask(c.idProofNumber())));
            accountsModel.set(List.of(), List.of());
        });
    }

    private Integer selectedId() {
        int row = table.selectedModelRow();
        return row < 0 ? null : (Integer) model.value(row, "Id");
    }

    private void showAccounts() {
        Integer id = selectedId();
        if (id != null) {
            Gui.attempt(this, () -> accountsModel.setReport(app.reports.accountsOfCustomer(id)));
        }
    }

    private void register() {
        CustomerEditor editor = new CustomerEditor(null);
        if (Gui.showForm(this, "Register customer", editor.form)
                && Gui.attempt(this, () -> {
                    Customer c = app.customers.register(editor.read());
                    Gui.info(this, "Customer registered", "Customer " + c.fullName() + " registered with id " + c.customerId() + ".");
                })) {
            refresh();
        }
    }

    private void edit() {
        Integer id = selectedId();
        if (id == null) {
            Gui.info(this, "Edit customer", "Select a customer in the table first.");
            return;
        }
        Customer[] current = new Customer[1];
        if (!Gui.attempt(this, () -> current[0] = app.customers.get(id))) {
            return;
        }
        CustomerEditor editor = new CustomerEditor(current[0]);
        if (Gui.showForm(this, "Edit customer " + id, editor.form)
                && Gui.attempt(this, () -> app.customers.update(id, editor.read()))) {
            refresh();
        }
    }

    /** The register/edit form. */
    private static final class CustomerEditor {
        final FormPanel form = new FormPanel();
        final JTextField first = new JTextField(20);
        final JTextField last = new JTextField(20);
        final JTextField dob = new JTextField(10);
        final JComboBox<Gender> gender = new JComboBox<>(Gender.values());
        final JTextField email = new JTextField(24);
        final JTextField phone = new JTextField(14);
        final JTextField address = new JTextField(28);
        final JTextField city = new JTextField(16);
        final JComboBox<IdProofType> idType = new JComboBox<>(IdProofType.values());
        final JTextField idNumber = new JTextField(16);

        CustomerEditor(Customer c) {
            if (c != null) {
                first.setText(c.firstName());
                last.setText(c.lastName());
                dob.setText(c.dateOfBirth().toString());
                gender.setSelectedItem(c.gender());
                email.setText(c.email());
                phone.setText(c.phone());
                address.setText(c.address());
                city.setText(c.city());
                idType.setSelectedItem(c.idProofType());
                idNumber.setText(c.idProofNumber());
            }
            form.add("First name", first).add("Last name", last).add("Date of birth (yyyy-mm-dd)", dob)
                    .add("Gender", gender).add("Email", email).add("Phone", phone).add("Address", address)
                    .add("City", city).add("ID proof type", idType).add("ID proof number", idNumber)
                    .note("Account holders and borrowers must be at least 18 years old.");
        }

        CustomerForm read() {
            return new CustomerForm(Gui.text(first), Gui.text(last), Gui.date(dob, "Date of birth"),
                    (Gender) gender.getSelectedItem(), Gui.text(email), Gui.text(phone), Gui.text(address),
                    Gui.text(city), (IdProofType) idType.getSelectedItem(), Gui.text(idNumber));
        }
    }
}
