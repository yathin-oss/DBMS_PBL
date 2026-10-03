package com.bankdbms.tools;

import com.bankdbms.app.AppContext;
import com.bankdbms.config.DbConfig;
import com.bankdbms.ledger.PostingFaultInjector;
import com.bankdbms.model.Account;
import com.bankdbms.model.AccountStatus;
import com.bankdbms.model.Beneficiary;
import com.bankdbms.model.Customer;
import com.bankdbms.model.CustomerForm;
import com.bankdbms.model.Gender;
import com.bankdbms.model.IdProofType;
import com.bankdbms.model.LoanInstallment;
import com.bankdbms.model.LoanPosition;
import com.bankdbms.model.LoanType;
import com.bankdbms.model.PostedTransaction;
import com.bankdbms.report.InvariantResult;
import com.bankdbms.util.AdjustableBankClock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the demonstration data set by driving the REAL services through seven months of
 * banking activity (March to September 2026) on an adjustable clock. Every row therefore obeys
 * the same business rules and posting logic as live operations, and the ledger invariants hold.
 *
 * <p>Run once against a freshly installed database (schema + reference data, no customers):
 * <pre>  java -cp target/bank-dbms.jar com.bankdbms.tools.SampleDataLoader</pre>
 * {@code scripts/export_sample_data.sh} then dumps the result into {@code sql/05_sample_data.sql}.
 */
public final class SampleDataLoader {

    private final AppContext app;
    private final AdjustableBankClock clock;

    private SampleDataLoader(AppContext app, AdjustableBankClock clock) {
        this.app = app;
        this.clock = clock;
    }

    public static void main(String[] args) {
        AdjustableBankClock clock = new AdjustableBankClock(LocalDateTime.of(2026, 3, 1, 9, 0));
        AppContext app = new AppContext(DbConfig.load(), clock, PostingFaultInjector.NONE);
        if (!app.customers.list().isEmpty()) {
            System.err.println("Refusing to load sample data: the database already contains customers.");
            System.exit(1);
        }
        new SampleDataLoader(app, clock).load();
        List<InvariantResult> results = app.reports.verifyLedger();
        results.forEach(r -> System.out.println((r.passed() ? "PASS " : "FAIL ") + r.invariant()));
        System.out.println("Sample data loaded.");
    }

    /** Events are queued with their business time and executed in chronological order. */
    private record Event(LocalDateTime time, int sequence, Runnable action) {
    }

    private final List<Event> timeline = new ArrayList<>();
    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<String, Customer> customers = new HashMap<>();
    private final Map<String, Beneficiary> beneficiaries = new HashMap<>();
    private final Map<String, LoanPosition> loans = new HashMap<>();
    private final Map<String, PostedTransaction> postings = new HashMap<>();
    private final Map<String, Integer> branches = new HashMap<>();

    private void at(int month, int day, int hour, int minute, Runnable action) {
        timeline.add(new Event(LocalDateTime.of(2026, month, day, hour, minute), timeline.size(), action));
    }

    private void runTimeline() {
        timeline.sort(Comparator.comparing(Event::time).thenComparing(Event::sequence));
        for (Event e : timeline) {
            clock.set(e.time());
            e.action().run();
        }
    }

    private static BigDecimal m(String amount) {
        return new BigDecimal(amount);
    }

    private int acc(String key) {
        return accounts.get(key).accountId();
    }

    private void customer(String key, String first, String last, LocalDate dob, Gender g, String phone,
                          String address, String city, IdProofType idType, String idNumber) {
        at(3, 1, 9, 30, () -> {
            String email = (first + "." + last).toLowerCase() + "@example.com";
            customers.put(key, app.customers.register(
                    new CustomerForm(first, last, dob, g, email, phone, address, city, idType, idNumber)));
        });
    }

    private void open(int month, int day, String key, String owner, String branch, int productId, String deposit) {
        at(month, day, 10, 30, () -> accounts.put(key, app.accounts.open(customers.get(owner).customerId(),
                branches.get(branch), productId, new BigDecimal(deposit))));
    }

    private void beneficiary(int month, int day, String key, String owner, String payeeAccount, String nickname) {
        at(month, day, 9, 0, () -> beneficiaries.put(key, app.beneficiaries.add(customers.get(owner).customerId(),
                accounts.get(payeeAccount).accountNumber(), nickname)));
    }

    private void deposit(int month, int day, int hour, String account, String amount, String narration) {
        at(month, day, hour, 0, () -> app.cash.deposit(acc(account), m(amount), narration));
    }

    private void withdraw(int month, int day, int hour, String account, String amount, String narration) {
        at(month, day, hour, 0, () -> app.cash.withdraw(acc(account), m(amount), narration));
    }

    private void payBeneficiary(int month, int day, int hour, String from, String beneficiary, String amount,
                                String remarks) {
        at(month, day, hour, 0, () -> app.transfers.transferToBeneficiary(acc(from),
                beneficiaries.get(beneficiary).beneficiaryId(), m(amount), remarks));
    }

    private void repayNext(int month, int day, String loan) {
        at(month, day, 9, 30, () -> {
            LoanPosition p = loans.get(loan);
            LoanInstallment next = app.loans.schedule(p.loanId()).stream()
                    .filter(i -> !"PAID".equals(i.status())).findFirst().orElseThrow();
            app.loans.repayNextInstallment(p.loanId(), next.installmentAmount());
        });
    }

    private void load() {
        // ---------------- Branches ----------------
        at(3, 1, 9, 0, () -> {
            branches.put("MUM", app.branches.create("BDBS0000102", "Andheri West", "14 Link Road, Andheri West",
                    "Mumbai", "02226300102").branchId());
            branches.put("PUN", app.branches.create("BDBS0000203", "Shivajinagar", "8 FC Road, Shivajinagar",
                    "Pune", "02025500203").branchId());
            branches.put("BLR", app.branches.create("BDBS0000304", "Koramangala", "80 Feet Road, Koramangala",
                    "Bengaluru", "08041100304").branchId());
        });

        // ---------------- Customers ----------------
        customer("aarav", "Aarav", "Sharma", LocalDate.of(1985, 4, 12), Gender.MALE, "9820011001",
                "301 Sea Breeze, Juhu", "Mumbai", IdProofType.PAN, "ABKPS4821K");
        customer("priya", "Priya", "Sharma", LocalDate.of(1987, 9, 3), Gender.FEMALE, "9820011002",
                "301 Sea Breeze, Juhu", "Mumbai", IdProofType.PAN, "BCLPS7314M");
        customer("rohan", "Rohan", "Mehta", LocalDate.of(1979, 1, 25), Gender.MALE, "9822022003",
                "12 Prabhat Road", "Pune", IdProofType.PASSPORT, "Z4829173");
        customer("ananya", "Ananya", "Iyer", LocalDate.of(1995, 7, 19), Gender.FEMALE, "9845033004",
                "5 HSR Layout Sector 2", "Bengaluru", IdProofType.AADHAAR, "482915736204");
        customer("vikram", "Vikram", "Singh", LocalDate.of(1968, 11, 30), Gender.MALE, "9822044005",
                "44 Model Colony", "Pune", IdProofType.PAN, "CDQPS2290H");
        customer("sneha", "Sneha", "Kulkarni", LocalDate.of(1992, 2, 14), Gender.FEMALE, "9845055006",
                "17 Indiranagar 2nd Stage", "Bengaluru", IdProofType.VOTER_ID, "KAR1928374");
        customer("arjun", "Arjun", "Nair", LocalDate.of(1990, 6, 8), Gender.MALE, "9820066007",
                "9 Powai Lake View", "Mumbai", IdProofType.DRIVING_LICENCE, "MH0220190012345");
        customer("kavya", "Kavya", "Reddy", LocalDate.of(1998, 12, 21), Gender.FEMALE, "9845077008",
                "22 BTM Layout", "Bengaluru", IdProofType.PAN, "EFRPR5521Q");
        customer("farhan", "Farhan", "Qureshi", LocalDate.of(1983, 3, 17), Gender.MALE, "9820088009",
                "6 Bandra Reclamation", "Mumbai", IdProofType.PASSPORT, "K7731905");
        customer("meera", "Meera", "Joshi", LocalDate.of(2001, 8, 5), Gender.FEMALE, "9822099010",
                "3 Kothrud Depot Road", "Pune", IdProofType.AADHAAR, "731904528816");

        // ---------------- Accounts (1 SAVINGS, 2 CURRENT, 3 SALARY) ----------------
        open(3, 2, "A1", "aarav", "MUM", 1, "50000.00");                 // joint savings
        open(3, 4, "A2", "rohan", "PUN", 2, "250000.00");                // business current
        at(3, 5, 12, 30, () -> app.accounts.addJointHolder(acc("A1"), customers.get("priya").customerId()));
        open(3, 6, "A3", "ananya", "BLR", 3, "0.00");
        open(3, 9, "A4", "vikram", "PUN", 1, "120000.00");
        open(3, 10, "A5", "sneha", "BLR", 1, "30000.00");
        open(3, 12, "A6", "arjun", "MUM", 1, "15000.00");
        open(3, 16, "A7", "kavya", "BLR", 3, "0.00");
        open(3, 18, "A8", "farhan", "MUM", 2, "80000.00");
        open(3, 20, "A9", "meera", "PUN", 1, "5000.00");
        open(4, 1, "A10", "vikram", "PUN", 2, "25000.00");
        open(4, 2, "A11", "priya", "MUM", 3, "0.00");

        // ---------------- Beneficiaries ----------------
        beneficiary(3, 30, "salaryAnanya", "rohan", "A3", "Ananya - salary");
        beneficiary(3, 30, "salaryKavya", "rohan", "A7", "Kavya - salary");
        beneficiary(4, 3, "salaryPriya", "rohan", "A11", "Priya - salary");
        beneficiary(4, 4, "landlord", "ananya", "A5", "Landlord - Sneha");
        beneficiary(4, 4, "snehaToAnanya", "sneha", "A3", "Ananya");
        beneficiary(9, 14, "builder", "aarav", "A2", "Builder - Mehta Constructions");

        // ---------------- Loans ----------------
        at(3, 15, 12, 0, () -> loans.put("vehicle", app.loans.disburse(customers.get("vikram").customerId(), acc("A4"),
                LoanType.VEHICLE, m("300000.00"), m("9.00"), 3)));
        withdraw(3, 16, 11, "A4", "300000.00", "Payment to car dealer");
        at(4, 6, 12, 0, () -> loans.put("personal", app.loans.disburse(customers.get("arjun").customerId(), acc("A6"),
                LoanType.PERSONAL, m("200000.00"), m("13.50"), 12)));
        withdraw(4, 8, 14, "A6", "150000.00", "Home renovation");
        at(9, 15, 12, 0, () -> loans.put("home", app.loans.disburse(customers.get("aarav").customerId(), acc("A1"),
                LoanType.HOME, m("2500000.00"), m("8.40"), 240)));
        payBeneficiary(9, 16, 11, "A1", "builder", "2400000.00", "Flat 1203 down payment");

        // Vehicle loan: all three instalments on time -> CLOSED.
        repayNext(4, 15, "vehicle");
        deposit(5, 10, 10, "A4", "150000.00", "Pension arrears");
        repayNext(5, 15, "vehicle");
        deposit(6, 10, 10, "A4", "100000.00", "Fixed income maturity");
        repayNext(6, 15, "vehicle");
        // Personal loan: May and June on time, July 5 days late, August on time,
        // September (due 2026-09-06) left unpaid -> OVERDUE.
        repayNext(5, 6, "personal");
        repayNext(6, 6, "personal");
        repayNext(7, 11, "personal");
        repayNext(8, 6, "personal");

        // ---------------- Monthly activity ----------------
        for (Month month : List.of(Month.MARCH, Month.APRIL, Month.MAY, Month.JUNE, Month.JULY, Month.AUGUST,
                Month.SEPTEMBER)) {
            int mo = month.getValue();
            if (mo >= 4) {
                deposit(mo, 1, 10, "A6", "20000.00", "Salary cash deposit");
                payBeneficiary(mo, 5, 9, "A3", "landlord", "15000.00", "Rent");
                deposit(mo, 12, 17, "A1", "25000.00", "Monthly savings");
                withdraw(mo, 15, 13, "A3", "10000.00", "ATM withdrawal");
                withdraw(mo, 15, 14, "A7", "5000.00", "ATM withdrawal");
            }
            deposit(mo, 25, 11, "A2", "200000.00", "Business receipts");
            int lastDay = month.length(false);
            payBeneficiary(mo, lastDay, 18, "A2", "salaryAnanya", "45000.00", "Salary");
            payBeneficiary(mo, lastDay, 18, "A2", "salaryKavya", "38000.00", "Salary");
            if (mo >= 4) {
                payBeneficiary(mo, lastDay, 18, "A2", "salaryPriya", "52000.00", "Salary");
            }
        }

        // ---------------- Charges, reversals, lifecycle ----------------
        at(4, 10, 12, 0, () -> app.charges.apply(acc("A2"), 2, null));           // cheque book fee
        withdraw(4, 20, 15, "A8", "20000.00", "Cash withdrawal");
        deposit(5, 2, 11, "A9", "3000.00", "Cash deposit");
        at(6, 18, 12, 10, () -> postings.put("wrong", app.cash.deposit(acc("A5"), m("9999.00"), "Cash deposit")));
        at(6, 18, 12, 40, () -> app.reversals.reverse(postings.get("wrong").transaction().referenceNo(),
                "Deposit keyed to wrong account"));
        at(6, 30, 20, 0, () -> {
            for (String savings : List.of("A1", "A4", "A5", "A6")) {
                app.charges.apply(acc(savings), 1, null);                      // quarterly SMS alert fee
            }
        });
        at(7, 1, 10, 0, () -> app.transfers.transfer(acc("A4"), acc("A10"), m("30000.00"), null, "Own account top-up"));
        at(7, 1, 10, 30, () -> app.charges.apply(acc("A8"), 4, null));         // annual maintenance fee
        at(7, 31, 15, 0, () -> app.accounts.close(acc("A9"), "Customer relocating abroad"));
        at(8, 1, 10, 0, () -> app.beneficiaries.deactivate(beneficiaries.get("snehaToAnanya").beneficiaryId()));
        at(8, 15, 11, 0, () -> postings.put("fee", app.charges.apply(acc("A4"), 3, null)));
        at(8, 15, 11, 20, () -> app.reversals.reverse(postings.get("fee").transaction().referenceNo(),
                "Fee waived: senior citizen"));
        at(8, 20, 10, 0, () -> app.accounts.changeStatus(acc("A8"), AccountStatus.BLOCKED, "KYC documents expired"));
        at(9, 10, 10, 0, () -> app.accounts.changeStatus(acc("A10"), AccountStatus.FROZEN, "Court attachment order"));
        deposit(9, 20, 12, "A10", "5000.00", "Rent received (credits allowed while frozen)");

        runTimeline();
    }
}
