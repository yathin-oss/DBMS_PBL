package com.bankdbms.service;

import com.bankdbms.dao.AccountHolderDao;
import com.bankdbms.dao.CustomerDao;
import com.bankdbms.dao.LoanDao;
import com.bankdbms.dao.LoanRepaymentDao;
import com.bankdbms.dao.LoanScheduleDao;
import com.bankdbms.dao.ReportDao;
import com.bankdbms.db.SqlErrorTranslator;
import com.bankdbms.db.TransactionRunner;
import com.bankdbms.exception.AccountStateException;
import com.bankdbms.exception.BusinessRuleException;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.ledger.AccountLocker;
import com.bankdbms.ledger.BalanceRule;
import com.bankdbms.ledger.LockedAccount;
import com.bankdbms.ledger.PostingEngine;
import com.bankdbms.ledger.PostingLine;
import com.bankdbms.ledger.PostingRequest;
import com.bankdbms.model.AccountStatus;
import com.bankdbms.model.Customer;
import com.bankdbms.model.InternalAccount;
import com.bankdbms.model.Loan;
import com.bankdbms.model.LoanInstallment;
import com.bankdbms.model.LoanPosition;
import com.bankdbms.model.LoanType;
import com.bankdbms.model.PostedTransaction;
import com.bankdbms.model.TransactionType;
import com.bankdbms.util.BankClock;
import com.bankdbms.util.LoanCalculator;
import com.bankdbms.util.Money;
import com.bankdbms.util.ReferenceGenerator;
import com.bankdbms.util.Validator;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loan subsystem (FR-26 to FR-30, BR-31 to BR-36).
 * Disbursement: DR LOAN_RECEIVABLE / CR customer. Repayment of one instalment:
 * DR customer (instalment) / CR LOAN_RECEIVABLE (principal) / CR INTEREST_INCOME (interest).
 */
public class LoanService {

    public static final BigDecimal MAX_RATE = new BigDecimal("36");
    public static final int MAX_TENURE = 360;
    private static final int LOAN_NUMBER_ATTEMPTS = 5;

    /** Result of repaying one instalment. */
    public record Repayment(int loanId, int installmentNo, PostedTransaction posted) {
    }

    private final TransactionRunner tx;
    private final LoanDao loanDao;
    private final LoanScheduleDao scheduleDao;
    private final LoanRepaymentDao repaymentDao;
    private final CustomerDao customerDao;
    private final AccountHolderDao holderDao;
    private final ReportDao reportDao;
    private final AccountLocker locker;
    private final PostingEngine engine;
    private final InternalAccounts internal;
    private final ReferenceGenerator references;
    private final BankClock clock;

    public LoanService(TransactionRunner tx, LoanDao loanDao, LoanScheduleDao scheduleDao,
                       LoanRepaymentDao repaymentDao, CustomerDao customerDao, AccountHolderDao holderDao,
                       ReportDao reportDao, AccountLocker locker, PostingEngine engine, InternalAccounts internal,
                       ReferenceGenerator references, BankClock clock) {
        this.tx = tx;
        this.loanDao = loanDao;
        this.scheduleDao = scheduleDao;
        this.repaymentDao = repaymentDao;
        this.customerDao = customerDao;
        this.holderDao = holderDao;
        this.reportDao = reportDao;
        this.locker = locker;
        this.engine = engine;
        this.internal = internal;
        this.references = references;
        this.clock = clock;
    }

    /**
     * Sanctions and disburses a loan in one transaction: ledger posting, loan row, and the full
     * reducing-balance schedule (BR-33).
     */
    public LoanPosition disburse(int customerId, int accountId, LoanType loanType, BigDecimal principal,
                                 BigDecimal annualRatePercent, int tenureMonths) {
        if (loanType == null) {
            throw new ValidationException("Loan type is required.");
        }
        BigDecimal amount = Money.requirePositive(principal, "Loan principal");
        BigDecimal rate = requireRate(annualRatePercent);
        if (tenureMonths < 1 || tenureMonths > MAX_TENURE) {
            throw new ValidationException("Tenure must be between 1 and " + MAX_TENURE + " months.");
        }
        return tx.inTransaction(conn -> {
            requireAdultBorrower(conn, customerId);
            int receivable = internal.id(conn, InternalAccount.LOAN_RECEIVABLE);
            Map<Integer, LockedAccount> locked = locker.lock(conn, List.of(accountId, receivable));
            LockedAccount account = locked.get(accountId);
            if (!account.isCustomerAccount() || account.account().status() != AccountStatus.ACTIVE) {
                throw new AccountStateException("Loans can only be disbursed to an ACTIVE customer account.");
            }
            if (!holderDao.isHolder(conn, accountId, customerId)) {
                throw new BusinessRuleException("The borrower must be a holder of account "
                        + account.account().accountNumber() + ".");
            }
            PostedTransaction posted = engine.post(conn, PostingRequest.of(TransactionType.LOAN_DISBURSEMENT,
                    loanType + " loan disbursement",
                    PostingLine.debit(receivable, amount, BalanceRule.NON_NEGATIVE),
                    PostingLine.credit(accountId, amount)));
            int loanId = insertLoan(conn, accountId, customerId, loanType, amount, rate, tenureMonths,
                    posted.transaction().transactionId());
            scheduleDao.insertAll(conn, loanId,
                    LoanCalculator.schedule(amount, rate, tenureMonths, posted.transaction().postedAt().toLocalDate()));
            return reportDao.loanPosition(conn, loanId).get(0);
        });
    }

    /**
     * Repays exactly the earliest unpaid instalment (BR-34). The amount must equal that instalment's
     * amount; partial and advance payments are out of scope (Review 1 decision).
     */
    public Repayment repayNextInstallment(int loanId, BigDecimal amount) {
        BigDecimal paid = Money.requirePositive(amount, "Repayment amount");
        return tx.inTransaction(conn -> {
            Loan loan = loanDao.findByIdForUpdate(conn, loanId)
                    .orElseThrow(() -> new NotFoundException("Loan " + loanId + " not found."));
            LoanInstallment next = scheduleDao.findNextUnpaid(conn, loanId)
                    .orElseThrow(() -> new BusinessRuleException("Loan " + loan.loanNumber() + " is already fully repaid."));
            if (paid.compareTo(next.installmentAmount()) != 0) {
                throw new ValidationException("Instalment " + next.installmentNo() + " of loan " + loan.loanNumber()
                        + " is " + Money.format(next.installmentAmount()) + "; the repayment must be exactly that amount.");
            }
            int receivable = internal.id(conn, InternalAccount.LOAN_RECEIVABLE);
            int interestIncome = internal.id(conn, InternalAccount.INTEREST_INCOME);
            locker.lock(conn, List.of(loan.accountId(), receivable, interestIncome));
            List<PostingLine> lines = new ArrayList<>();
            lines.add(PostingLine.debit(loan.accountId(), paid, BalanceRule.MIN_BALANCE));
            lines.add(PostingLine.credit(receivable, next.principalComponent()));
            if (next.interestComponent().signum() > 0) {
                lines.add(PostingLine.credit(interestIncome, next.interestComponent()));
            }
            PostedTransaction posted = engine.post(conn, new PostingRequest(TransactionType.LOAN_REPAYMENT,
                    "EMI " + next.installmentNo() + "/" + loan.tenureMonths() + " for " + loan.loanNumber(), null, lines));
            repaymentDao.insert(conn, posted.transaction().transactionId(), loanId, next.installmentNo());
            return new Repayment(loanId, next.installmentNo(), posted);
        });
    }

    public LoanPosition position(int loanId) {
        List<LoanPosition> rows = tx.readOnly(conn -> reportDao.loanPosition(conn, loanId));
        if (rows.isEmpty()) {
            throw new NotFoundException("Loan " + loanId + " not found.");
        }
        return rows.get(0);
    }

    public LoanPosition positionByNumber(String loanNumber) {
        String number = Validator.requireText(loanNumber, "Loan number", 14).toUpperCase();
        Loan loan = tx.readOnly(conn -> loanDao.findByNumber(conn, number))
                .orElseThrow(() -> new NotFoundException("Loan " + number + " not found."));
        return position(loan.loanId());
    }

    public List<LoanPosition> allPositions() {
        return tx.readOnly(reportDao::loanPositions);
    }

    public List<LoanPosition> positionsForCustomer(int customerId) {
        return tx.readOnly(conn -> reportDao.loanPositionsForCustomer(conn, customerId));
    }

    public List<LoanInstallment> schedule(int loanId) {
        return tx.readOnly(conn -> scheduleDao.findByLoan(conn, loanId));
    }

    private static BigDecimal requireRate(BigDecimal rate) {
        if (rate == null) {
            throw new ValidationException("Interest rate is required.");
        }
        if (rate.signum() <= 0 || rate.compareTo(MAX_RATE) > 0) {
            throw new ValidationException("Interest rate must be above 0% and at most 36% per year.");
        }
        if (rate.stripTrailingZeros().scale() > 2) {
            throw new ValidationException("Interest rate can have at most 2 decimal places.");
        }
        return rate;
    }

    private void requireAdultBorrower(Connection conn, int customerId) throws SQLException {
        Customer customer = customerDao.findById(conn, customerId)
                .orElseThrow(() -> new NotFoundException("Customer " + customerId + " not found."));
        if (!Validator.isAdult(customer.dateOfBirth(), clock.today())) {
            throw new BusinessRuleException(customer.fullName() + " is under " + Validator.ADULT_AGE
                    + " and cannot borrow.");
        }
    }

    private int insertLoan(Connection conn, int accountId, int customerId, LoanType type, BigDecimal principal,
                           BigDecimal rate, int tenure, long disbursementTxnId) throws SQLException {
        for (int attempt = 1; ; attempt++) {
            Loan loan = new Loan(0, references.loanNumber(clock.today()), accountId, customerId, type, principal, rate,
                    tenure, disbursementTxnId);
            try {
                return loanDao.insert(conn, loan);
            } catch (SQLException e) {
                if (!SqlErrorTranslator.isDuplicateOn(e, "uq_loan_number") || attempt == LOAN_NUMBER_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }
}
