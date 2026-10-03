package com.bankdbms.service;

import com.bankdbms.app.AppContext;
import com.bankdbms.exception.AccountStateException;
import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.InsufficientFundsException;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.model.Account;
import com.bankdbms.model.AccountStatus;
import com.bankdbms.model.EntrySide;
import com.bankdbms.model.InternalAccount;
import com.bankdbms.model.PostedTransaction;
import com.bankdbms.model.TransactionType;
import com.bankdbms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CashServiceTest extends IntegrationTestBase {

    @Test
    @DisplayName("TC-DEP-01 valid deposit: DR CASH / CR account, balances and reference recorded")
    void deposit() {
        Account a = newAccount(SAVINGS, "1000.00");
        BigDecimal cashBefore = internalBalance(InternalAccount.CASH);
        PostedTransaction t = app.cash.deposit(a.accountId(), money("2500.50"), "Salary cash");
        assertEquals(TransactionType.DEPOSIT, t.transaction().type());
        assertTrue(t.transaction().referenceNo().matches("TX[0-9]{8}[A-Z0-9]{6}"));
        assertEquals(money("2500.50"), t.amount());
        assertEquals(money("3500.50"), t.entryFor(a.accountId(), EntrySide.CREDIT).balanceAfter());
        assertEquals(money("3500.50"), balance(a.accountId()));
        assertEquals(cashBefore.add(money("2500.50")), internalBalance(InternalAccount.CASH));
    }

    @ParameterizedTest(name = "TC-DEP-02 invalid deposit amount rejected: {0}")
    @ValueSource(strings = {"0", "0.00", "-100", "10.555"})
    void invalidDepositAmount(String amount) {
        Account a = newAccount(SAVINGS, "1000.00");
        long txns = count("SELECT COUNT(*) FROM bank_transaction");
        assertThrows(ValidationException.class, () -> app.cash.deposit(a.accountId(), new BigDecimal(amount), null));
        assertEquals(txns, count("SELECT COUNT(*) FROM bank_transaction"));
        assertEquals(money("1000.00"), balance(a.accountId()));
    }

    @Test
    @DisplayName("TC-DEP-03 FROZEN accepts deposits; BLOCKED and CLOSED reject them")
    void depositByStatus() {
        Account frozen = newAccount(SAVINGS, "1000.00");
        app.accounts.changeStatus(frozen.accountId(), AccountStatus.FROZEN, "Hold");
        app.cash.deposit(frozen.accountId(), money("100.00"), null);
        assertEquals(money("1100.00"), balance(frozen.accountId()));

        Account blocked = newAccount(SAVINGS, "1000.00");
        app.accounts.changeStatus(blocked.accountId(), AccountStatus.BLOCKED, "KYC");
        assertThrows(AccountStateException.class, () -> app.cash.deposit(blocked.accountId(), money("100.00"), null));

        Account closed = newAccount(SAVINGS, "1000.00");
        app.accounts.close(closed.accountId(), "Done");
        assertThrows(AccountStateException.class, () -> app.cash.deposit(closed.accountId(), money("100.00"), null));
    }

    @Test
    @DisplayName("TC-DEP-04 unknown account and internal accounts are rejected")
    void depositInvalidAccount() {
        assertThrows(NotFoundException.class, () -> app.cash.deposit(999_999, money("10.00"), null));
        assertThrows(BusinessRuleException.class, () -> app.cash.deposit(FEE_INCOME_ID, money("10.00"), null));
    }

    @Test
    @DisplayName("TC-DEP-05 database failure during a deposit rolls back completely")
    void depositRollback() {
        Account a = newAccount(SAVINGS, "1000.00");
        BigDecimal cashBefore = internalBalance(InternalAccount.CASH);
        long entries = count("SELECT COUNT(*) FROM transaction_entry");
        AppContext failing = contextWithFault(index -> {
            throw new SQLException("Simulated database failure", "08S01", 2013);
        });
        assertThrows(RuntimeException.class, () -> failing.cash.deposit(a.accountId(), money("500.00"), null));
        assertEquals(money("1000.00"), balance(a.accountId()));
        assertEquals(cashBefore, internalBalance(InternalAccount.CASH));
        assertEquals(entries, count("SELECT COUNT(*) FROM transaction_entry"));
    }

    @Test
    @DisplayName("TC-WDR-01 valid withdrawal: DR account / CR CASH")
    void withdraw() {
        Account a = newAccount(SAVINGS, "5000.00");
        PostedTransaction t = app.cash.withdraw(a.accountId(), money("1200.00"), "ATM");
        assertEquals(money("3800.00"), balance(a.accountId()));
        assertEquals(money("3800.00"), t.entryFor(a.accountId(), EntrySide.DEBIT).balanceAfter());
    }

    @Test
    @DisplayName("TC-WDR-02 withdrawal must leave the minimum balance (exactly the minimum is allowed)")
    void minimumBalance() {
        Account a = newAccount(SAVINGS, "1500.00");           // SAVINGS minimum balance = 500.00
        InsufficientFundsException e = assertThrows(InsufficientFundsException.class,
                () -> app.cash.withdraw(a.accountId(), money("1000.01"), null));
        assertTrue(e.getMessage().contains("minimum balance of 500.00"));
        app.cash.withdraw(a.accountId(), money("1000.00"), null);
        assertEquals(money("500.00"), balance(a.accountId()));
    }

    @Test
    @DisplayName("TC-WDR-03 withdrawal above the balance is rejected (insufficient funds)")
    void insufficientFunds() {
        Account a = newAccount(SALARY, "300.00");             // SALARY minimum balance = 0.00
        assertThrows(InsufficientFundsException.class, () -> app.cash.withdraw(a.accountId(), money("300.01"), null));
        assertEquals(money("300.00"), balance(a.accountId()));
        app.cash.withdraw(a.accountId(), money("300.00"), null);
        assertEquals(money("0.00"), balance(a.accountId()));
    }

    @ParameterizedTest(name = "TC-WDR-04 invalid withdrawal amount rejected: {0}")
    @ValueSource(strings = {"0", "-1.00", "1.001"})
    void invalidWithdrawalAmount(String amount) {
        Account a = newAccount(SAVINGS, "5000.00");
        assertThrows(ValidationException.class, () -> app.cash.withdraw(a.accountId(), new BigDecimal(amount), null));
    }

    @Test
    @DisplayName("TC-WDR-05 FROZEN and BLOCKED accounts cannot be debited")
    void withdrawByStatus() {
        Account frozen = newAccount(SAVINGS, "5000.00");
        app.accounts.changeStatus(frozen.accountId(), AccountStatus.FROZEN, "Hold");
        AccountStateException e = assertThrows(AccountStateException.class,
                () -> app.cash.withdraw(frozen.accountId(), money("100.00"), null));
        assertTrue(e.getMessage().contains("FROZEN"));
        Account blocked = newAccount(SAVINGS, "5000.00");
        app.accounts.changeStatus(blocked.accountId(), AccountStatus.BLOCKED, "KYC");
        assertThrows(AccountStateException.class, () -> app.cash.withdraw(blocked.accountId(), money("100.00"), null));
    }
}
