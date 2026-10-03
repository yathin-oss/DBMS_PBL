package com.bankdbms.service;

import com.bankdbms.dao.AccountDao;
import com.bankdbms.dao.AccountTypeDao;
import com.bankdbms.dao.BranchDao;
import com.bankdbms.dao.CustomerDao;
import com.bankdbms.dao.ReportDao;
import com.bankdbms.db.TransactionRunner;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.exception.ValidationException;
import com.bankdbms.model.Account;
import com.bankdbms.model.Customer;
import com.bankdbms.report.AccountStatement;
import com.bankdbms.report.InvariantResult;
import com.bankdbms.report.ReportTable;
import com.bankdbms.report.StatementLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/** Statements and reports (FR-31 to FR-37). Everything is read from the database views. */
public class ReportService {

    private final TransactionRunner tx;
    private final ReportDao reportDao;
    private final AccountDao accountDao;
    private final AccountTypeDao accountTypeDao;
    private final BranchDao branchDao;
    private final CustomerDao customerDao;

    public ReportService(TransactionRunner tx, ReportDao reportDao, AccountDao accountDao,
                         AccountTypeDao accountTypeDao, BranchDao branchDao, CustomerDao customerDao) {
        this.tx = tx;
        this.reportDao = reportDao;
        this.accountDao = accountDao;
        this.accountTypeDao = accountTypeDao;
        this.branchDao = branchDao;
        this.customerDao = customerDao;
    }

    /**
     * Statement for [fromDate, toDate] inclusive (BR-37): opening balance (last balance before the
     * period), every entry in the period with its running balance, totals and closing balance.
     */
    public AccountStatement statement(int accountId, LocalDate fromDate, LocalDate toDate) {
        requireRange(fromDate, toDate);
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime toExclusive = toDate.plusDays(1).atStartOfDay();
        return tx.readOnly(conn -> {
            Account account = accountDao.findById(conn, accountId)
                    .orElseThrow(() -> new NotFoundException("Account " + accountId + " not found."));
            String product = accountTypeDao.findById(conn, account.accountTypeId()).orElseThrow().typeName();
            String branch = branchDao.findById(conn, account.branchId()).orElseThrow().branchName();
            String holders = customerDao.findHoldersOfAccount(conn, accountId).stream()
                    .map(Customer::fullName).collect(Collectors.joining(", "));
            BigDecimal opening = reportDao.balanceBefore(conn, accountId, from);
            List<StatementLine> lines = reportDao.statementLines(conn, accountId, from, toExclusive);
            BigDecimal debits = sum(lines, true);
            BigDecimal credits = sum(lines, false);
            BigDecimal closing = lines.isEmpty() ? opening : lines.get(lines.size() - 1).balanceAfter();
            return new AccountStatement(account.accountNumber(), product, branch, holders, account.status().name(),
                    fromDate, toDate, opening, lines, debits, credits, closing);
        });
    }

    public ReportTable accountBalances() {
        return tx.readOnly(reportDao::accountBalances);
    }

    public ReportTable accountsOfCustomer(int customerId) {
        return tx.readOnly(conn -> reportDao.accountsOfCustomer(conn, customerId));
    }

    public ReportTable searchAccounts(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new ValidationException("Search text is required.");
        }
        return tx.readOnly(conn -> reportDao.searchAccounts(conn, keyword.strip()));
    }

    public ReportTable transactionAudit(LocalDate fromDate, LocalDate toDate) {
        requireRange(fromDate, toDate);
        return tx.readOnly(conn -> reportDao.transactionAudit(conn, fromDate.atStartOfDay(),
                toDate.plusDays(1).atStartOfDay()));
    }

    public ReportTable transactionDetail(String referenceNo) {
        if (referenceNo == null || referenceNo.isBlank()) {
            throw new ValidationException("Transaction reference is required.");
        }
        ReportTable table = tx.readOnly(conn -> reportDao.transactionDetail(conn, referenceNo.strip().toUpperCase()));
        if (table.size() == 0) {
            throw new NotFoundException("Transaction " + referenceNo.strip() + " not found.");
        }
        return table;
    }

    public ReportTable transfers(LocalDate fromDate, LocalDate toDate) {
        requireRange(fromDate, toDate);
        return tx.readOnly(conn -> reportDao.transfers(conn, fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay()));
    }

    public ReportTable loanOutstanding() {
        return tx.readOnly(reportDao::loanOutstanding);
    }

    public ReportTable loanRepayments(Integer loanId) {
        return tx.readOnly(conn -> reportDao.loanRepayments(conn, loanId));
    }

    public ReportTable branchSummary() {
        return tx.readOnly(reportDao::branchSummary);
    }

    public ReportTable trialBalance() {
        return tx.readOnly(reportDao::trialBalance);
    }

    public ReportTable chargeSummary() {
        return tx.readOnly(reportDao::chargeSummary);
    }

    public ReportTable dailySummary(LocalDate fromDate, LocalDate toDate) {
        requireRange(fromDate, toDate);
        return tx.readOnly(conn -> reportDao.dailySummary(conn, fromDate.atStartOfDay(),
                toDate.plusDays(1).atStartOfDay()));
    }

    /** Runs all integrity invariants (sql/06_verification_queries.sql). */
    public List<InvariantResult> verifyLedger() {
        return tx.readOnly(reportDao::verifyInvariants);
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ValidationException("Both start and end dates are required.");
        }
        if (from.isAfter(to)) {
            throw new ValidationException("Start date must not be after end date.");
        }
    }

    private static BigDecimal sum(List<StatementLine> lines, boolean debits) {
        return lines.stream().map(debits ? StatementLine::debit : StatementLine::credit)
                .filter(v -> v != null).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }
}
