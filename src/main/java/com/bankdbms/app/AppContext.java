package com.bankdbms.app;

import com.bankdbms.config.DbConfig;
import com.bankdbms.dao.AccountDao;
import com.bankdbms.dao.AccountHolderDao;
import com.bankdbms.dao.AccountTypeDao;
import com.bankdbms.dao.BeneficiaryDao;
import com.bankdbms.dao.BranchDao;
import com.bankdbms.dao.ChargeDao;
import com.bankdbms.dao.ChargeTypeDao;
import com.bankdbms.dao.CustomerDao;
import com.bankdbms.dao.LoanDao;
import com.bankdbms.dao.LoanRepaymentDao;
import com.bankdbms.dao.LoanScheduleDao;
import com.bankdbms.dao.ReportDao;
import com.bankdbms.dao.StatusHistoryDao;
import com.bankdbms.dao.TransactionDao;
import com.bankdbms.dao.TransferDao;
import com.bankdbms.db.ConnectionFactory;
import com.bankdbms.db.TransactionRunner;
import com.bankdbms.ledger.AccountLocker;
import com.bankdbms.ledger.PostingEngine;
import com.bankdbms.ledger.PostingFaultInjector;
import com.bankdbms.service.AccountService;
import com.bankdbms.service.AccountTypeService;
import com.bankdbms.service.BeneficiaryService;
import com.bankdbms.service.BranchService;
import com.bankdbms.service.CashService;
import com.bankdbms.service.ChargeService;
import com.bankdbms.service.CustomerService;
import com.bankdbms.service.InternalAccounts;
import com.bankdbms.service.LoanService;
import com.bankdbms.service.ReportService;
import com.bankdbms.service.ReversalService;
import com.bankdbms.service.TransferService;
import com.bankdbms.util.BankClock;
import com.bankdbms.util.ReferenceGenerator;

/**
 * Composition root: creates every DAO and service once and wires them together by constructor
 * (plain Java, no framework). Tests build their own context with an adjustable clock or a fault
 * injector.
 */
public final class AppContext {

    public final ConnectionFactory connections;
    public final TransactionRunner tx;
    public final BranchService branches;
    public final AccountTypeService accountTypes;
    public final CustomerService customers;
    public final AccountService accounts;
    public final BeneficiaryService beneficiaries;
    public final CashService cash;
    public final TransferService transfers;
    public final ChargeService charges;
    public final ReversalService reversals;
    public final LoanService loans;
    public final ReportService reports;

    public AppContext(DbConfig config) {
        this(config, BankClock.system(), PostingFaultInjector.NONE);
    }

    public AppContext(DbConfig config, BankClock clock, PostingFaultInjector faultInjector) {
        connections = new ConnectionFactory(config);
        tx = new TransactionRunner(connections);

        BranchDao branchDao = new BranchDao();
        AccountTypeDao accountTypeDao = new AccountTypeDao();
        CustomerDao customerDao = new CustomerDao();
        AccountDao accountDao = new AccountDao();
        AccountHolderDao holderDao = new AccountHolderDao();
        StatusHistoryDao historyDao = new StatusHistoryDao();
        TransactionDao transactionDao = new TransactionDao();
        TransferDao transferDao = new TransferDao();
        BeneficiaryDao beneficiaryDao = new BeneficiaryDao();
        ChargeTypeDao chargeTypeDao = new ChargeTypeDao();
        ChargeDao chargeDao = new ChargeDao();
        LoanDao loanDao = new LoanDao();
        LoanScheduleDao scheduleDao = new LoanScheduleDao();
        LoanRepaymentDao repaymentDao = new LoanRepaymentDao();
        ReportDao reportDao = new ReportDao();

        ReferenceGenerator references = new ReferenceGenerator();
        AccountLocker locker = new AccountLocker(accountDao, accountTypeDao);
        PostingEngine engine = new PostingEngine(locker, accountDao, transactionDao, references, clock, faultInjector);
        InternalAccounts internal = new InternalAccounts(accountDao);

        branches = new BranchService(tx, branchDao, clock);
        accountTypes = new AccountTypeService(tx, accountTypeDao);
        customers = new CustomerService(tx, customerDao, clock);
        accounts = new AccountService(tx, accountDao, holderDao, historyDao, customerDao, branchDao, accountTypeDao,
                loanDao, locker, engine, internal, references, clock);
        beneficiaries = new BeneficiaryService(tx, beneficiaryDao, customerDao, accountDao, accountTypeDao, holderDao,
                clock);
        cash = new CashService(tx, locker, engine, internal);
        transfers = new TransferService(tx, locker, engine, holderDao, beneficiaryDao, transferDao);
        charges = new ChargeService(tx, locker, engine, internal, chargeTypeDao, chargeDao);
        reversals = new ReversalService(tx, transactionDao, locker, engine);
        loans = new LoanService(tx, loanDao, scheduleDao, repaymentDao, customerDao, holderDao, reportDao, locker,
                engine, internal, references, clock);
        reports = new ReportService(tx, reportDao, accountDao, accountTypeDao, branchDao, customerDao);
    }
}
