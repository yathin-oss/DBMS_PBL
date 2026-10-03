package com.bankdbms.service;

import com.bankdbms.app.AppContext;
import com.bankdbms.exception.AccountStateException;
import com.bankdbms.exception.BankException;
import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.InsufficientFundsException;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.model.Account;
import com.bankdbms.model.AccountStatus;
import com.bankdbms.model.Beneficiary;
import com.bankdbms.model.Customer;
import com.bankdbms.model.PostedTransaction;
import com.bankdbms.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferServiceTest extends IntegrationTestBase {

    /** Snapshot of everything a transfer could change, to prove "no partial transfer". */
    private record Snapshot(BigDecimal from, BigDecimal to, long headers, long entries, long transfers) {
    }

    private Snapshot snapshot(Account from, Account to) {
        return new Snapshot(balance(from.accountId()), balance(to.accountId()),
                count("SELECT COUNT(*) FROM bank_transaction"), count("SELECT COUNT(*) FROM transaction_entry"),
                count("SELECT COUNT(*) FROM transfer"));
    }

    @Test
    @DisplayName("TC-TRF-01 own-account transfer (shared holder) needs no beneficiary")
    void ownAccountTransfer() {
        Customer owner = newCustomer();
        Account savings = newAccount(owner, SAVINGS, "10000.00");
        Account current = newAccount(owner, CURRENT, "5000.00");
        PostedTransaction t = app.transfers.transfer(savings.accountId(), current.accountId(), money("2500.00"), null, "Top-up");
        assertEquals(money("7500.00"), balance(savings.accountId()));
        assertEquals(money("7500.00"), balance(current.accountId()));
        assertEquals(2, t.entries().size());
        assertEquals(1, count("SELECT COUNT(*) FROM transfer WHERE transaction_id = ? AND beneficiary_id IS NULL",
                t.transaction().transactionId()));
    }

    @Test
    @DisplayName("TC-TRF-02 third-party transfer through an ACTIVE beneficiary")
    void beneficiaryTransfer() {
        Customer payer = newCustomer();
        Account from = newAccount(payer, SAVINGS, "10000.00");
        Account to = newAccount(SAVINGS, "1000.00");
        Beneficiary b = app.beneficiaries.add(payer.customerId(), to.accountNumber(), "Rent");
        app.transfers.transferToBeneficiary(from.accountId(), b.beneficiaryId(), money("4000.00"), "October rent");
        assertEquals(money("6000.00"), balance(from.accountId()));
        assertEquals(money("5000.00"), balance(to.accountId()));
    }

    @Test
    @DisplayName("TC-TRF-03 third-party transfer without a beneficiary is rejected")
    void thirdPartyNeedsBeneficiary() {
        Account from = newAccount(SAVINGS, "10000.00");
        Account to = newAccount(SAVINGS, "1000.00");
        Snapshot before = snapshot(from, to);
        assertThrows(BusinessRuleException.class,
                () -> app.transfers.transfer(from.accountId(), to.accountId(), money("100.00"), null, null));
        assertEquals(before, snapshot(from, to));
    }

    @Test
    @DisplayName("TC-TRF-04 inactive, foreign or mismatched beneficiaries are rejected")
    void invalidBeneficiaries() {
        Customer payer = newCustomer();
        Account from = newAccount(payer, SAVINGS, "10000.00");
        Account to = newAccount(SAVINGS, "1000.00");
        Account other = newAccount(SAVINGS, "1000.00");
        Beneficiary b = app.beneficiaries.add(payer.customerId(), to.accountNumber(), "Payee");
        assertThrows(BusinessRuleException.class,
                () -> app.transfers.transfer(from.accountId(), other.accountId(), money("10.00"), b.beneficiaryId(), null));
        Customer stranger = newCustomer();
        Beneficiary foreign = app.beneficiaries.add(stranger.customerId(), to.accountNumber(), "Theirs");
        assertThrows(BusinessRuleException.class,
                () -> app.transfers.transfer(from.accountId(), to.accountId(), money("10.00"), foreign.beneficiaryId(), null));
        app.beneficiaries.deactivate(b.beneficiaryId());
        assertThrows(BusinessRuleException.class,
                () -> app.transfers.transferToBeneficiary(from.accountId(), b.beneficiaryId(), money("10.00"), null));
        assertEquals(money("10000.00"), balance(from.accountId()));
    }

    @Test
    @DisplayName("TC-TRF-05 insufficient balance (minimum balance rule) leaves both accounts unchanged")
    void insufficientBalance() {
        Customer owner = newCustomer();
        Account from = newAccount(owner, SAVINGS, "1500.00");
        Account to = newAccount(owner, CURRENT, "5000.00");
        Snapshot before = snapshot(from, to);
        assertThrows(InsufficientFundsException.class,
                () -> app.transfers.transfer(from.accountId(), to.accountId(), money("1000.01"), null, null));
        assertEquals(before, snapshot(from, to));
    }

    @Test
    @DisplayName("TC-TRF-06 invalid destination, same account and internal accounts are rejected")
    void invalidAccounts() {
        Account from = newAccount(SAVINGS, "5000.00");
        assertThrows(NotFoundException.class,
                () -> app.transfers.transfer(from.accountId(), 999_999, money("10.00"), null, null));
        assertThrows(ValidationException.class,
                () -> app.transfers.transfer(from.accountId(), from.accountId(), money("10.00"), null, null));
        assertThrows(BusinessRuleException.class,
                () -> app.transfers.transfer(from.accountId(), CASH_ID, money("10.00"), null, null));
    }

    @ParameterizedTest(name = "TC-TRF-07 invalid transfer amount rejected: {0}")
    @ValueSource(strings = {"0", "-50.00", "0.001"})
    void invalidAmount(String amount) {
        Customer owner = newCustomer();
        Account from = newAccount(owner, SAVINGS, "5000.00");
        Account to = newAccount(owner, CURRENT, "5000.00");
        assertThrows(ValidationException.class,
                () -> app.transfers.transfer(from.accountId(), to.accountId(), new BigDecimal(amount), null, null));
    }

    @Test
    @DisplayName("TC-TRF-08 inactive accounts: FROZEN source rejected, FROZEN destination accepted, BLOCKED destination rejected")
    void statuses() {
        Customer owner = newCustomer();
        Account a = newAccount(owner, CURRENT, "20000.00");
        Account b = newAccount(owner, SAVINGS, "5000.00");
        app.accounts.changeStatus(a.accountId(), AccountStatus.FROZEN, "Hold");
        assertThrows(AccountStateException.class,
                () -> app.transfers.transfer(a.accountId(), b.accountId(), money("10.00"), null, null));
        app.transfers.transfer(b.accountId(), a.accountId(), money("100.00"), null, null);
        app.accounts.changeStatus(a.accountId(), AccountStatus.BLOCKED, "KYC");
        assertThrows(AccountStateException.class,
                () -> app.transfers.transfer(b.accountId(), a.accountId(), money("100.00"), null, null));
        assertEquals(money("20100.00"), balance(a.accountId()));
    }

    @Test
    @DisplayName("TC-TRF-09 ROLLBACK: database failure after the DEBIT entry -> no partial transfer")
    void rollbackAfterDebit() {
        assertRollback(0);
    }

    @Test
    @DisplayName("TC-TRF-10 ROLLBACK: database failure after both entries, before commit -> nothing persisted")
    void rollbackAfterCredit() {
        assertRollback(1);
    }

    private void assertRollback(int failAfterEntry) {
        Customer owner = newCustomer();
        Account from = newAccount(owner, SAVINGS, "10000.00");
        Account to = newAccount(owner, CURRENT, "5000.00");
        Snapshot before = snapshot(from, to);
        AppContext failing = contextWithFault(index -> {
            if (index == failAfterEntry) {
                throw new SQLException("Simulated connection failure during transfer", "08S01", 2013);
            }
        });
        BankException e = assertThrows(BankException.class,
                () -> failing.transfers.transfer(from.accountId(), to.accountId(), money("3000.00"), null, null));
        assertTrue(e.getMessage().contains("not completed"), e.getMessage());
        assertEquals(before, snapshot(from, to), "a partial transfer was persisted");
        app.transfers.transfer(from.accountId(), to.accountId(), money("3000.00"), null, null);
        assertEquals(money("7000.00"), balance(from.accountId()));
    }

    @Test
    @DisplayName("TC-TRF-11 CONCURRENCY: parallel transfers in both directions conserve money, no lost update")
    void concurrentTransfers() throws Exception {
        Customer owner = newCustomer();
        Account a = newAccount(owner, CURRENT, "100000.00");
        Account b = newAccount(owner, CURRENT, "100000.00");
        int threads = 8;
        int perThread = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> results = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            boolean aToB = t % 2 == 0;
            results.add(pool.submit(() -> {
                int ok = 0;
                for (int i = 0; i < perThread; i++) {
                    int from = aToB ? a.accountId() : b.accountId();
                    int to = aToB ? b.accountId() : a.accountId();
                    app.transfers.transfer(from, to, money("100.00"), null, "concurrency");
                    ok++;
                }
                return ok;
            }));
        }
        int completed = 0;
        for (Future<Integer> f : results) {
            completed += f.get(120, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertEquals(threads * perThread, completed);
        // Equal numbers of transfers in each direction: both balances return to their start values.
        assertEquals(money("100000.00"), balance(a.accountId()));
        assertEquals(money("100000.00"), balance(b.accountId()));
        assertEquals(threads * perThread, count("SELECT COUNT(*) FROM transfer t JOIN transaction_entry e "
                + "ON e.transaction_id = t.transaction_id AND e.entry_side = 'DEBIT' WHERE e.account_id IN (?, ?)",
                a.accountId(), b.accountId()));
    }
}
