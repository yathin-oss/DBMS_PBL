package com.bankdbms.service;

import com.bankdbms.exception.DuplicateRecordException;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.model.Customer;
import com.bankdbms.model.CustomerForm;
import com.bankdbms.model.Gender;
import com.bankdbms.model.IdProofType;
import com.bankdbms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerServiceTest extends IntegrationTestBase {

    private CustomerForm form(String email, String idNumber) {
        return new CustomerForm("Meera", "Iyer", LocalDate.of(1988, 2, 29), Gender.FEMALE, email, "9876543210",
                "12 MG Road", "Bengaluru", IdProofType.PASSPORT, idNumber);
    }

    @Test
    @DisplayName("TC-CUS-01 register a valid customer (email normalised to lower case)")
    void register() {
        Customer c = app.customers.register(form("Meera.Iyer@Example.com", "P1234567"));
        assertTrue(c.customerId() > 0);
        assertEquals("meera.iyer@example.com", c.email());
        assertEquals(c, app.customers.get(c.customerId()));
    }

    @Test
    @DisplayName("TC-CUS-02 duplicate email is rejected with a friendly message")
    void duplicateEmail() {
        app.customers.register(form("dup@example.com", "P2000001"));
        DuplicateRecordException e = assertThrows(DuplicateRecordException.class,
                () -> app.customers.register(form("dup@example.com", "P2000002")));
        assertEquals("A customer with this email address already exists.", e.getMessage());
    }

    @Test
    @DisplayName("TC-CUS-03 invalid data is rejected before reaching the database")
    void invalidData() {
        assertThrows(ValidationException.class, () -> app.customers.register(form("not-an-email", "P3000001")));
        CustomerForm blankName = new CustomerForm(" ", "Iyer", LocalDate.of(1990, 1, 1), Gender.MALE, "b@example.com",
                "9876543210", "Road", "City", IdProofType.PAN, "ABCDE0000A");
        assertThrows(ValidationException.class, () -> app.customers.register(blankName));
        CustomerForm future = new CustomerForm("A", "B", clock.today().plusDays(1), Gender.MALE, "f@example.com",
                "9876543210", "Road", "City", IdProofType.PAN, "ABCDE0000B");
        assertThrows(ValidationException.class, () -> app.customers.register(future));
        assertEquals(0, count("SELECT COUNT(*) FROM customer WHERE email IN ('b@example.com','f@example.com')"));
    }

    @Test
    @DisplayName("TC-CUS-04 update customer details")
    void update() {
        Customer c = app.customers.register(form("upd@example.com", "P4000001"));
        CustomerForm changed = new CustomerForm("Meera", "Iyer-Rao", c.dateOfBirth(), Gender.FEMALE, "upd@example.com",
                "9000000001", "7 New Street", "Chennai", IdProofType.PASSPORT, "P4000001");
        Customer updated = app.customers.update(c.customerId(), changed);
        assertEquals("Iyer-Rao", updated.lastName());
        assertEquals("Chennai", updated.city());
    }

    @Test
    @DisplayName("TC-CUS-05 reading or updating an unknown customer reports 'not found'")
    void unknownCustomer() {
        assertThrows(NotFoundException.class, () -> app.customers.get(999_999));
        assertThrows(NotFoundException.class, () -> app.customers.update(999_999, form("x@example.com", "P5000001")));
    }

    @Test
    @DisplayName("TC-CUS-06 search by partial name/email; LIKE wildcards in input are treated literally")
    void search() {
        Customer c = app.customers.register(form("searchme@example.com", "P6000001"));
        assertTrue(app.customers.search("searchme").stream().anyMatch(x -> x.customerId() == c.customerId()));
        assertTrue(app.customers.search("Meera Iy").stream().anyMatch(x -> x.customerId() == c.customerId()));
        assertTrue(app.customers.search("%").isEmpty());
        assertFalse(app.customers.list().isEmpty());
    }
}
