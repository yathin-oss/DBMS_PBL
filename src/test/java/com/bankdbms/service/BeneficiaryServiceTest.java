package com.bankdbms.service;

import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.DuplicateRecordException;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.model.Account;
import com.bankdbms.model.Beneficiary;
import com.bankdbms.model.BeneficiaryStatus;
import com.bankdbms.model.Customer;
import com.bankdbms.model.InternalAccount;
import com.bankdbms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeneficiaryServiceTest extends IntegrationTestBase {

    @Test
    @DisplayName("TC-BEN-01 add a beneficiary and find it by nickname or account number")
    void addAndSearch() {
        Customer owner = newCustomer();
        Account payee = newAccount(SAVINGS, "1000.00");
        Beneficiary b = app.beneficiaries.add(owner.customerId(), payee.accountNumber(), "Landlord");
        assertEquals(BeneficiaryStatus.ACTIVE, b.status());
        assertTrue(app.beneficiaries.search("Landlord").stream().anyMatch(x -> x.beneficiaryId() == b.beneficiaryId()));
        assertTrue(app.beneficiaries.search(payee.accountNumber()).stream()
                .anyMatch(x -> x.beneficiaryId() == b.beneficiaryId()));
        assertEquals(1, app.beneficiaries.listForCustomer(owner.customerId()).size());
    }

    @Test
    @DisplayName("TC-BEN-02 invalid beneficiary relationships are rejected")
    void invalidRelationships() {
        Customer owner = newCustomer();
        Account own = newAccount(owner, SAVINGS, "1000.00");
        assertThrows(BusinessRuleException.class, () -> app.beneficiaries.add(owner.customerId(), own.accountNumber(), "Me"));
        assertThrows(BusinessRuleException.class,
                () -> app.beneficiaries.add(owner.customerId(), InternalAccount.CASH.accountNumber(), "Cash"));
        assertThrows(NotFoundException.class, () -> app.beneficiaries.add(owner.customerId(), "899999999998", "Ghost"));
        Account closed = newAccount(SAVINGS, "1000.00");
        app.accounts.close(closed.accountId(), "test");
        assertThrows(BusinessRuleException.class,
                () -> app.beneficiaries.add(owner.customerId(), closed.accountNumber(), "Closed"));
        Account payee = newAccount(SAVINGS, "1000.00");
        app.beneficiaries.add(owner.customerId(), payee.accountNumber(), "Friend");
        assertThrows(DuplicateRecordException.class,
                () -> app.beneficiaries.add(owner.customerId(), payee.accountNumber(), "Friend again"));
    }

    @Test
    @DisplayName("TC-BEN-03 rename a beneficiary; a duplicate nickname for the same customer is rejected")
    void rename() {
        Customer owner = newCustomer();
        Beneficiary a = app.beneficiaries.add(owner.customerId(), newAccount(SAVINGS, "1000").accountNumber(), "Alpha");
        app.beneficiaries.add(owner.customerId(), newAccount(SAVINGS, "1000").accountNumber(), "Beta");
        assertEquals("Gamma", app.beneficiaries.rename(a.beneficiaryId(), "Gamma").nickname());
        assertThrows(DuplicateRecordException.class, () -> app.beneficiaries.rename(a.beneficiaryId(), "Beta"));
    }

    @Test
    @DisplayName("TC-BEN-04 deactivate and reactivate (never deleted)")
    void deactivateReactivate() {
        Customer owner = newCustomer();
        Beneficiary b = app.beneficiaries.add(owner.customerId(), newAccount(SAVINGS, "1000").accountNumber(), "Tutor");
        Beneficiary off = app.beneficiaries.deactivate(b.beneficiaryId());
        assertEquals(BeneficiaryStatus.INACTIVE, off.status());
        assertNotNull(off.deactivatedAt());
        assertThrows(BusinessRuleException.class, () -> app.beneficiaries.deactivate(b.beneficiaryId()));
        Beneficiary on = app.beneficiaries.reactivate(b.beneficiaryId());
        assertEquals(BeneficiaryStatus.ACTIVE, on.status());
        assertNull(on.deactivatedAt());
        assertEquals(1, count("SELECT COUNT(*) FROM beneficiary WHERE beneficiary_id = ?", b.beneficiaryId()));
    }
}
