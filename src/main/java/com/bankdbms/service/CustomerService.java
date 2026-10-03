package com.bankdbms.service;

import com.bankdbms.dao.CustomerDao;
import com.bankdbms.db.TransactionRunner;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.model.Customer;
import com.bankdbms.model.CustomerForm;
import com.bankdbms.util.BankClock;
import com.bankdbms.util.Validator;

import java.util.List;

/** Customer management (FR-01 to FR-05, BR-01, BR-03). */
public class CustomerService {

    private final TransactionRunner tx;
    private final CustomerDao customerDao;
    private final BankClock clock;

    public CustomerService(TransactionRunner tx, CustomerDao customerDao, BankClock clock) {
        this.tx = tx;
        this.customerDao = customerDao;
        this.clock = clock;
    }

    public Customer register(CustomerForm form) {
        CustomerForm valid = validate(form);
        return tx.inTransaction(conn -> customerDao.findById(conn, customerDao.insert(conn, valid, clock.now())).orElseThrow());
    }

    public Customer update(int customerId, CustomerForm form) {
        CustomerForm valid = validate(form);
        return tx.inTransaction(conn -> {
            if (customerDao.update(conn, customerId, valid, clock.now()) == 0 && customerDao.findById(conn, customerId).isEmpty()) {
                throw new NotFoundException("Customer " + customerId + " not found.");
            }
            return customerDao.findById(conn, customerId).orElseThrow();
        });
    }

    public Customer get(int customerId) {
        return tx.readOnly(conn -> customerDao.findById(conn, customerId))
                .orElseThrow(() -> new NotFoundException("Customer " + customerId + " not found."));
    }

    public List<Customer> list() {
        return tx.readOnly(customerDao::findAll);
    }

    public List<Customer> search(String keyword) {
        String kw = Validator.requireText(keyword, "Search text", 100);
        return tx.readOnly(conn -> customerDao.search(conn, kw));
    }

    public List<Customer> holdersOf(int accountId) {
        return tx.readOnly(conn -> customerDao.findHoldersOfAccount(conn, accountId));
    }

    private CustomerForm validate(CustomerForm f) {
        if (f == null) {
            throw new ValidationException("Customer details are required.");
        }
        if (f.gender() == null) {
            throw new ValidationException("Gender is required.");
        }
        if (f.idProofType() == null) {
            throw new ValidationException("ID proof type is required.");
        }
        return new CustomerForm(
                Validator.requireText(f.firstName(), "First name", 50),
                Validator.requireText(f.lastName(), "Last name", 50),
                Validator.requireDateOfBirth(f.dateOfBirth(), clock.today()),
                f.gender(),
                Validator.requireEmail(f.email()),
                Validator.requirePhone(f.phone()),
                Validator.requireText(f.address(), "Address", 255),
                Validator.requireText(f.city(), "City", 60),
                f.idProofType(),
                Validator.requireText(f.idProofNumber(), "ID proof number", 20).toUpperCase());
    }
}
