package com.bankdbms.service;

import com.bankdbms.dao.BranchDao;
import com.bankdbms.db.TransactionRunner;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.model.Branch;
import com.bankdbms.util.BankClock;
import com.bankdbms.util.Validator;

import java.util.List;

/** Branch management (FR-06, FR-07). */
public class BranchService {

    private final TransactionRunner tx;
    private final BranchDao branchDao;
    private final BankClock clock;

    public BranchService(TransactionRunner tx, BranchDao branchDao, BankClock clock) {
        this.tx = tx;
        this.branchDao = branchDao;
        this.clock = clock;
    }

    public Branch create(String ifscCode, String name, String address, String city, String phone) {
        String ifsc = Validator.requireIfsc(ifscCode);
        String branchName = Validator.requireText(name, "Branch name", 100);
        String addr = Validator.requireText(address, "Address", 255);
        String cityName = Validator.requireText(city, "City", 60);
        String tel = Validator.requirePhone(phone);
        return tx.inTransaction(conn -> {
            int id = branchDao.insert(conn, ifsc, branchName, addr, cityName, tel, clock.now());
            return branchDao.findById(conn, id).orElseThrow();
        });
    }

    public Branch get(int branchId) {
        return tx.readOnly(conn -> branchDao.findById(conn, branchId))
                .orElseThrow(() -> new NotFoundException("Branch " + branchId + " not found."));
    }

    public List<Branch> list() {
        return tx.readOnly(branchDao::findAll);
    }

    public List<Branch> search(String keyword) {
        String kw = Validator.requireText(keyword, "Search text", 100);
        return tx.readOnly(conn -> branchDao.search(conn, kw));
    }

    /** Updates contact details; IFSC code and name identify the branch and are not changed. */
    public Branch updateContact(int branchId, String address, String city, String phone) {
        String addr = Validator.requireText(address, "Address", 255);
        String cityName = Validator.requireText(city, "City", 60);
        String tel = Validator.requirePhone(phone);
        return tx.inTransaction(conn -> {
            if (branchDao.updateContact(conn, branchId, addr, cityName, tel) == 0) {
                throw new NotFoundException("Branch " + branchId + " not found.");
            }
            return branchDao.findById(conn, branchId).orElseThrow();
        });
    }
}
