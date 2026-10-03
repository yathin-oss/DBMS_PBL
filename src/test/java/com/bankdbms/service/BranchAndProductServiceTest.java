package com.bankdbms.service;

import com.bankdbms.exception.DuplicateRecordException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.model.AccountCategory;
import com.bankdbms.model.AccountType;
import com.bankdbms.model.Branch;
import com.bankdbms.model.EntrySide;
import com.bankdbms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchAndProductServiceTest extends IntegrationTestBase {

    @Test
    @DisplayName("TC-BR-01 create a branch; duplicate IFSC is rejected")
    void createBranch() {
        Branch b = app.branches.create("bdbs0000501", "Nashik Road", "Station Road", "Nashik", "02532000501");
        assertEquals("BDBS0000501", b.ifscCode());
        assertThrows(DuplicateRecordException.class,
                () -> app.branches.create("BDBS0000501", "Copy", "Road", "City", "02532000502"));
    }

    @Test
    @DisplayName("TC-BR-02 invalid IFSC and phone are rejected")
    void invalidBranch() {
        assertThrows(ValidationException.class, () -> app.branches.create("BAD", "X", "Road", "City", "02532000503"));
        assertThrows(ValidationException.class, () -> app.branches.create("BDBS0000503", "X", "Road", "City", "12"));
    }

    @Test
    @DisplayName("TC-BR-03 update branch contact details and search by city")
    void updateAndSearch() {
        Branch b = app.branches.create("BDBS0000504", "Kothrud", "Paud Road", "Pune", "02025000504");
        Branch updated = app.branches.updateContact(b.branchId(), "Karve Road", "Pune", "02025000999");
        assertEquals("Karve Road", updated.address());
        assertTrue(app.branches.search("Kothrud").stream().anyMatch(x -> x.branchId() == b.branchId()));
    }

    @Test
    @DisplayName("TC-AT-01 seeded customer products are SAVINGS, CURRENT and SALARY, all credit-normal")
    void seededProducts() {
        var codes = app.accountTypes.listProducts().stream().map(AccountType::typeCode).toList();
        assertTrue(codes.containsAll(java.util.List.of("SAVINGS", "CURRENT", "SALARY")));
        app.accountTypes.listProducts().forEach(t -> {
            assertEquals(AccountCategory.CUSTOMER, t.category());
            assertEquals(EntrySide.CREDIT, t.normalSide());
        });
    }

    @Test
    @DisplayName("TC-AT-02 create a product; inconsistent limits and duplicate code are rejected")
    void createProduct() {
        AccountType t = app.accountTypes.createProduct("senior_savings", "Senior Citizen Savings",
                new BigDecimal("500.00"), new BigDecimal("250.00"), 2, new BigDecimal("4.00"), "For age 60+");
        assertEquals("SENIOR_SAVINGS", t.typeCode());
        assertThrows(ValidationException.class, () -> app.accountTypes.createProduct("BAD_LIMITS", "Bad",
                new BigDecimal("100.00"), new BigDecimal("200.00"), 1, BigDecimal.ZERO, null));
        assertThrows(DuplicateRecordException.class, () -> app.accountTypes.createProduct("SENIOR_SAVINGS", "Again",
                BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, null));
    }
}
