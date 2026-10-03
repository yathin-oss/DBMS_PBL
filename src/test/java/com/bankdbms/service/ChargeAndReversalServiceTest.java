package com.bankdbms.service;

import com.bankdbms.exception.AccountStateException;
import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.InsufficientFundsException;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.model.Account;
import com.bankdbms.model.AccountStatus;
import com.bankdbms.model.Customer;
import com.bankdbms.model.InternalAccount;
import com.bankdbms.model.LoanType;
import com.bankdbms.model.PostedTransaction;
import com.bankdbms.model.TransactionStatus;
import com.bankdbms.model.TransactionType;
import com.bankdbms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChargeAndReversalServiceTest extends IntegrationTestBase {

    private static final int SMS_ALERT_FEE = 1;
    private static final int ANNUAL_MAINTENANCE_FEE = 4;

    @Test
    @DisplayName("TC-CHG-01 service charge at the default amount: DR account / CR FEE_INCOME + charge record")
    void defaultCharge() {
        Account a = newAccount(SAVINGS, "2000.00");
        BigDecimal feeBefore = internalBalance(InternalAccount.FEE_INCOME);
        PostedTransaction t = app.charges.apply(a.accountId(), SMS_ALERT_FEE, null);
        assertEquals(TransactionType.CHARGE, t.transaction().type());
        assertEquals(money("1975.00"), balance(a.accountId()));
        assertEquals(feeBefore.add(money("25.00")), internalBalance(InternalAccount.FEE_INCOME));
        assertEquals(1, count("SELECT COUNT(*) FROM charge WHERE transaction_id = ? AND charge_type_id = ?",
                t.transaction().transactionId(), SMS_ALERT_FEE));
    }

    @Test
    @DisplayName("TC-CHG-02 charge with an overridden amount")
    void overriddenCharge() {
        Account a = newAccount(SAVINGS, "2000.00");
        app.charges.apply(a.accountId(), ANNUAL_MAINTENANCE_FEE, money("350.00"));
        assertEquals(money("1650.00"), balance(a.accountId()));
    }

    @Test
    @DisplayName("TC-CHG-03 a charge may go below the minimum balance but never below zero")
    void chargeBalanceRule() {
        Account a = newAccount(SAVINGS, "1000.00");                    // minimum balance 500.00
        app.charges.apply(a.accountId(), ANNUAL_MAINTENANCE_FEE, money("700.00"));
        assertEquals(money("300.00"), balance(a.accountId()));
        assertThrows(InsufficientFundsException.class,
                () -> app.charges.apply(a.accountId(), ANNUAL_MAINTENANCE_FEE, money("300.01")));
    }

    @Test
    @DisplayName("TC-CHG-04 charges are debits: rejected on FROZEN accounts; bad type/amount rejected")
    void chargeRejections() {
        Account a = newAccount(SAVINGS, "2000.00");
        assertThrows(NotFoundException.class, () -> app.charges.apply(a.accountId(), 999, null));
        assertThrows(ValidationException.class, () -> app.charges.apply(a.accountId(), SMS_ALERT_FEE, money("-5")));
        app.accounts.changeStatus(a.accountId(), AccountStatus.FROZEN, "Hold");
        assertThrows(AccountStateException.class, () -> app.charges.apply(a.accountId(), SMS_ALERT_FEE, null));
    }

    @Test
    @DisplayName("TC-REV-01 reverse a deposit: mirrored entries, original marked REVERSED, balances restored")
    void reverseDeposit() {
        Account a = newAccount(SAVINGS, "1000.00");
        BigDecimal cashBefore = internalBalance(InternalAccount.CASH);
        PostedTransaction deposit = app.cash.deposit(a.accountId(), money("750.00"), "Keyed wrong account");
        PostedTransaction reversal = app.reversals.reverse(deposit.transaction().referenceNo(), "Wrong account");
        assertEquals(TransactionType.REVERSAL, reversal.transaction().type());
        assertEquals(deposit.transaction().transactionId(), reversal.transaction().reversalOfId());
        assertEquals(money("1000.00"), balance(a.accountId()));
        assertEquals(cashBefore, internalBalance(InternalAccount.CASH));
        assertEquals(1, count("SELECT COUNT(*) FROM bank_transaction WHERE transaction_id = ? AND status = ?",
                deposit.transaction().transactionId(), TransactionStatus.REVERSED.name()));
    }

    @Test
    @DisplayName("TC-REV-02 a transaction can be reversed only once; a REVERSAL cannot be reversed")
    void reverseOnce() {
        Account a = newAccount(SAVINGS, "1000.00");
        PostedTransaction deposit = app.cash.deposit(a.accountId(), money("100.00"), null);
        PostedTransaction reversal = app.reversals.reverse(deposit.transaction().referenceNo(), "Error");
        assertThrows(BusinessRuleException.class, () -> app.reversals.reverse(deposit.transaction().referenceNo(), "Again"));
        assertThrows(BusinessRuleException.class, () -> app.reversals.reverse(reversal.transaction().referenceNo(), "x"));
    }

    @Test
    @DisplayName("TC-REV-03 reverse a transfer and waive a charge")
    void reverseTransferAndCharge() {
        Customer owner = newCustomer();
        Account from = newAccount(owner, SAVINGS, "5000.00");
        Account to = newAccount(owner, CURRENT, "5000.00");
        PostedTransaction transfer = app.transfers.transfer(from.accountId(), to.accountId(), money("1000.00"), null, null);
        app.reversals.reverse(transfer.transaction().referenceNo(), "Duplicate instruction");
        assertEquals(money("5000.00"), balance(from.accountId()));
        assertEquals(money("5000.00"), balance(to.accountId()));

        BigDecimal feeBefore = internalBalance(InternalAccount.FEE_INCOME);
        PostedTransaction charge = app.charges.apply(from.accountId(), SMS_ALERT_FEE, null);
        app.reversals.reverse(charge.transaction().referenceNo(), "Fee waived");
        assertEquals(money("5000.00"), balance(from.accountId()));
        assertEquals(feeBefore, internalBalance(InternalAccount.FEE_INCOME));
    }

    @Test
    @DisplayName("TC-REV-04 reversal obeys balance rules: a spent deposit cannot be reversed; nothing changes")
    void reversalNeedsFunds() {
        Account a = newAccount(SALARY, "0.00");
        PostedTransaction deposit = app.cash.deposit(a.accountId(), money("1000.00"), null);
        app.cash.withdraw(a.accountId(), money("900.00"), null);
        assertThrows(InsufficientFundsException.class,
                () -> app.reversals.reverse(deposit.transaction().referenceNo(), "Too late"));
        assertEquals(money("100.00"), balance(a.accountId()));
        assertEquals(0, count("SELECT COUNT(*) FROM bank_transaction WHERE reversal_of_id = ?",
                deposit.transaction().transactionId()));
    }

    @Test
    @DisplayName("TC-REV-05 loan transactions are not reversible; unknown references are reported")
    void nonReversible() {
        Customer owner = newCustomer();
        Account a = newAccount(owner, SAVINGS, "5000.00");
        app.loans.disburse(owner.customerId(), a.accountId(), LoanType.PERSONAL, money("10000.00"), money("12"), 6);
        String reference = app.reports.transactionAudit(clock.today(), clock.today()).rows().stream()
                .filter(r -> "LOAN_DISBURSEMENT".equals(r.get(2))).map(r -> (String) r.get(0)).reduce((x, y) -> y).orElseThrow();
        assertThrows(BusinessRuleException.class, () -> app.reversals.reverse(reference, "No"));
        assertThrows(NotFoundException.class, () -> app.reversals.reverse("TX20260101ZZZZZZ", "No"));
    }
}
