package com.bankdbms.support;

import com.bankdbms.app.AppContext;
import com.bankdbms.ledger.PostingFaultInjector;
import com.bankdbms.model.Account;
import com.bankdbms.model.Customer;
import com.bankdbms.model.CustomerForm;
import com.bankdbms.model.Gender;
import com.bankdbms.model.IdProofType;
import com.bankdbms.model.InternalAccount;
import com.bankdbms.report.InvariantResult;
import com.bankdbms.util.AdjustableBankClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Base class for tests that run the real services against MySQL.
 * The test database is rebuilt once per test class; each test creates its own customers and
 * accounts, and after every test all ledger invariants (I1-I11) must still hold.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class IntegrationTestBase {

    protected static final int SAVINGS = 1;
    protected static final int CURRENT = 2;
    protected static final int SALARY = 3;
    protected static final int HEAD_OFFICE = 1;
    protected static final int CASH_ID = 1;
    protected static final int LOAN_RECEIVABLE_ID = 2;
    protected static final int FEE_INCOME_ID = 3;
    protected static final int INTEREST_INCOME_ID = 4;

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    protected AdjustableBankClock clock;
    protected AppContext app;
    protected int branchId;

    @BeforeAll
    void setUpDatabase() {
        TestDatabase.rebuild();
        clock = new AdjustableBankClock(LocalDateTime.now());
        app = new AppContext(TestDatabase.config(), clock, PostingFaultInjector.NONE);
        branchId = app.branches.create("TEST0" + String.format("%06d", SEQUENCE.incrementAndGet()), "Test Branch",
                "1 Test Road", "Pune", "02000000000").branchId();
    }

    @AfterEach
    void ledgerInvariantsStillHold() {
        List<InvariantResult> results = app.reports.verifyLedger();
        for (InvariantResult r : results) {
            assertTrue(r.passed(), "Invariant violated: " + r.invariant() + " (" + r.violations() + ")");
        }
    }

    /** A context sharing this test's database and clock but with a fault injector in the posting engine. */
    protected AppContext contextWithFault(PostingFaultInjector injector) {
        return new AppContext(TestDatabase.config(), clock, injector);
    }

    protected Customer newCustomer() {
        return newCustomer(LocalDate.of(1990, 5, 17));
    }

    protected Customer newCustomer(LocalDate dob) {
        int n = SEQUENCE.incrementAndGet();
        return app.customers.register(new CustomerForm("Test" + n, "Customer", dob, Gender.FEMALE,
                "customer" + n + "." + System.nanoTime() + "@example.com", "98" + String.format("%08d", n),
                n + " Test Street", "Pune", IdProofType.PAN, "TESTP" + n + "X" + (System.nanoTime() % 100000)));
    }

    protected Account newAccount(int productId, String openingDeposit) {
        return newAccount(newCustomer(), productId, openingDeposit);
    }

    protected Account newAccount(Customer owner, int productId, String openingDeposit) {
        return app.accounts.open(owner.customerId(), branchId, productId, new BigDecimal(openingDeposit));
    }

    protected BigDecimal balance(int accountId) {
        return app.accounts.get(accountId).balance();
    }

    protected BigDecimal internalBalance(InternalAccount account) {
        return app.accounts.getByNumber(account.accountNumber()).balance();
    }

    protected static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    protected static long count(String sql, Object... params) {
        try (Connection c = TestDatabase.app(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
