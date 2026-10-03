package com.bankdbms.service;

import com.bankdbms.exception.AccountStateException;
import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.model.Account;
import com.bankdbms.model.AccountStatus;
import com.bankdbms.model.Customer;
import com.bankdbms.model.HolderRole;
import com.bankdbms.model.InternalAccount;
import com.bankdbms.model.StatusChange;
import com.bankdbms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceTest extends IntegrationTestBase {

    @Test
    @DisplayName("TC-ACC-01 open an account: PRIMARY holder, status history and opening deposit posted atomically")
    void openAccount() {
        BigDecimal cashBefore = internalBalance(InternalAccount.CASH);
        Customer owner = newCustomer();
        Account a = app.accounts.open(owner.customerId(), branchId, SAVINGS, money("5000.00"));
        assertEquals(AccountStatus.ACTIVE, a.status());
        assertEquals(money("5000.00"), a.balance());
        assertTrue(a.accountNumber().matches("[1-8][0-9]{11}"));
        assertEquals(HolderRole.PRIMARY, app.accounts.holders(a.accountId()).get(0).role());
        List<StatusChange> history = app.accounts.statusHistory(a.accountId());
        assertEquals(1, history.size());
        assertNull(history.get(0).oldStatus());
        assertEquals(cashBefore.add(money("5000.00")), internalBalance(InternalAccount.CASH));
        assertEquals(1, count("SELECT COUNT(*) FROM transaction_entry WHERE account_id = ?", a.accountId()));
    }

    @Test
    @DisplayName("TC-ACC-02 opening deposit below the product minimum is rejected and nothing is saved")
    void openingBelowMinimum() {
        Customer owner = newCustomer();
        long accountsBefore = count("SELECT COUNT(*) FROM account");
        ValidationException e = assertThrows(ValidationException.class,
                () -> app.accounts.open(owner.customerId(), branchId, SAVINGS, money("999.99")));
        assertTrue(e.getMessage().contains("1,000.00"));
        assertEquals(accountsBefore, count("SELECT COUNT(*) FROM account"));
    }

    @Test
    @DisplayName("TC-ACC-03 invalid customer, branch or product references are rejected")
    void invalidReferences() {
        Customer owner = newCustomer();
        assertThrows(NotFoundException.class, () -> app.accounts.open(999_999, branchId, SAVINGS, money("5000")));
        assertThrows(NotFoundException.class, () -> app.accounts.open(owner.customerId(), 999, SAVINGS, money("5000")));
        assertThrows(NotFoundException.class, () -> app.accounts.open(owner.customerId(), branchId, 999, money("5000")));
        assertThrows(BusinessRuleException.class, () -> app.accounts.open(owner.customerId(), branchId, 4, money("0")));
        assertThrows(ValidationException.class, () -> app.accounts.open(owner.customerId(), branchId, SAVINGS, money("-1")));
    }

    @Test
    @DisplayName("TC-ACC-04 a customer under 18 cannot open an account")
    void minorRejected() {
        Customer minor = newCustomer(clock.today().minusYears(17));
        assertThrows(BusinessRuleException.class, () -> app.accounts.open(minor.customerId(), branchId, SALARY, money("0")));
    }

    @Test
    @DisplayName("TC-ACC-05 zero-balance salary account opens with no ledger transaction")
    void zeroOpening() {
        Account a = newAccount(SALARY, "0.00");
        assertEquals(money("0.00"), a.balance());
        assertEquals(0, count("SELECT COUNT(*) FROM transaction_entry WHERE account_id = ?", a.accountId()));
    }

    @Test
    @DisplayName("TC-ACC-06 joint holders up to the product limit; duplicates and extra holders rejected")
    void jointHolders() {
        Account a = newAccount(SAVINGS, "2000.00");
        Customer joint = newCustomer();
        app.accounts.addJointHolder(a.accountId(), joint.customerId());
        assertEquals(2, app.accounts.holders(a.accountId()).size());
        assertThrows(BusinessRuleException.class, () -> app.accounts.addJointHolder(a.accountId(), joint.customerId()));
        Customer third = newCustomer();
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> app.accounts.addJointHolder(a.accountId(), third.customerId()));
        assertTrue(e.getMessage().contains("at most 2"));
    }

    @Test
    @DisplayName("TC-ACC-07 lifecycle changes are recorded in status history")
    void lifecycleHistory() {
        Account a = newAccount(CURRENT, "10000.00");
        app.accounts.changeStatus(a.accountId(), AccountStatus.FROZEN, "Court order");
        app.accounts.changeStatus(a.accountId(), AccountStatus.BLOCKED, "KYC expired");
        Account back = app.accounts.changeStatus(a.accountId(), AccountStatus.ACTIVE, "KYC renewed");
        assertEquals(AccountStatus.ACTIVE, back.status());
        List<StatusChange> history = app.accounts.statusHistory(a.accountId());
        assertEquals(4, history.size());
        assertEquals(AccountStatus.BLOCKED, history.get(3).oldStatus());
        assertEquals("KYC renewed", history.get(3).reason());
    }

    @Test
    @DisplayName("TC-ACC-08 invalid transitions are rejected (same status, CLOSED via change, internal account)")
    void invalidTransitions() {
        Account a = newAccount(CURRENT, "10000.00");
        assertThrows(AccountStateException.class, () -> app.accounts.changeStatus(a.accountId(), AccountStatus.ACTIVE, "x"));
        assertThrows(BusinessRuleException.class, () -> app.accounts.changeStatus(a.accountId(), AccountStatus.CLOSED, "x"));
        assertThrows(BusinessRuleException.class, () -> app.accounts.changeStatus(CASH_ID, AccountStatus.FROZEN, "x"));
        assertThrows(ValidationException.class, () -> app.accounts.changeStatus(a.accountId(), AccountStatus.FROZEN, " "));
    }

    @Test
    @DisplayName("TC-ACC-09 closing pays out the balance in the same transaction and is terminal")
    void closeAccount() {
        Account a = newAccount(SAVINGS, "3500.00");
        BigDecimal cashBefore = internalBalance(InternalAccount.CASH);
        Account closed = app.accounts.close(a.accountId(), "Customer request");
        assertEquals(AccountStatus.CLOSED, closed.status());
        assertNotNull(closed.closedAt());
        assertEquals(money("0.00"), closed.balance());
        assertEquals(cashBefore.subtract(money("3500.00")), internalBalance(InternalAccount.CASH));
        assertThrows(AccountStateException.class, () -> app.accounts.changeStatus(a.accountId(), AccountStatus.ACTIVE, "reopen"));
        assertThrows(AccountStateException.class, () -> app.accounts.close(a.accountId(), "again"));
    }

    @Test
    @DisplayName("TC-ACC-10 a FROZEN account cannot be closed")
    void cannotCloseFrozen() {
        Account a = newAccount(SAVINGS, "3500.00");
        app.accounts.changeStatus(a.accountId(), AccountStatus.FROZEN, "Hold");
        assertThrows(AccountStateException.class, () -> app.accounts.close(a.accountId(), "x"));
    }

    @Test
    @DisplayName("TC-ACC-11 lookup by number and by customer")
    void lookup() {
        Customer owner = newCustomer(LocalDate.of(1975, 3, 3));
        Account a = newAccount(owner, SAVINGS, "1000.00");
        Account b = newAccount(owner, CURRENT, "5000.00");
        assertEquals(a, app.accounts.getByNumber(a.accountNumber()));
        assertEquals(List.of(a.accountId(), b.accountId()),
                app.accounts.listByCustomer(owner.customerId()).stream().map(Account::accountId).toList());
        assertThrows(ValidationException.class, () -> app.accounts.getByNumber("123"));
        assertThrows(NotFoundException.class, () -> app.accounts.getByNumber("899999999999"));
    }
}
