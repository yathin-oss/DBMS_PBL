package com.bankdbms.service;

import com.bankdbms.dao.AccountTypeDao;
import com.bankdbms.db.TransactionRunner;
import com.bankdbms.exception.NotFoundException;
import com.bankdbms.model.AccountCategory;
import com.bankdbms.model.AccountType;
import com.bankdbms.model.EntrySide;
import com.bankdbms.util.Money;
import com.bankdbms.util.Validator;

import java.math.BigDecimal;
import java.util.List;

/** Account types as banking products (FR-10, BR-04). */
public class AccountTypeService {

    private final TransactionRunner tx;
    private final AccountTypeDao accountTypeDao;

    public AccountTypeService(TransactionRunner tx, AccountTypeDao accountTypeDao) {
        this.tx = tx;
        this.accountTypeDao = accountTypeDao;
    }

    public List<AccountType> listProducts() {
        return tx.readOnly(conn -> accountTypeDao.findByCategory(conn, AccountCategory.CUSTOMER));
    }

    public List<AccountType> listAll() {
        return tx.readOnly(accountTypeDao::findAll);
    }

    public AccountType get(int accountTypeId) {
        return tx.readOnly(conn -> accountTypeDao.findById(conn, accountTypeId))
                .orElseThrow(() -> new NotFoundException("Account type " + accountTypeId + " not found."));
    }

    /** Creates a new customer product. Customer products are always credit-normal (BR-06). */
    public AccountType createProduct(String code, String name, BigDecimal minOpeningBalance, BigDecimal minBalance,
                                     int maxHolders, BigDecimal interestRate, String description) {
        String typeCode = Validator.requireCode(code, "Type code", 20);
        String typeName = Validator.requireText(name, "Type name", 60);
        BigDecimal minOpening = Money.requireNonNegative(minOpeningBalance, "Minimum opening balance");
        BigDecimal minimum = Money.requireNonNegative(minBalance, "Minimum balance");
        Validator.require(minimum.compareTo(minOpening) <= 0,
                "Minimum balance cannot exceed the minimum opening balance.");
        Validator.require(maxHolders >= 1 && maxHolders <= 4, "Maximum holders must be between 1 and 4.");
        Validator.require(interestRate != null && interestRate.signum() >= 0 && interestRate.compareTo(new BigDecimal("100")) <= 0,
                "Interest rate must be between 0 and 100.");
        String desc = Validator.optionalText(description, "Description", 255);
        AccountType product = new AccountType(0, typeCode, typeName, AccountCategory.CUSTOMER, EntrySide.CREDIT,
                minOpening, minimum, maxHolders, interestRate, desc);
        return tx.inTransaction(conn -> accountTypeDao.findById(conn, accountTypeDao.insert(conn, product)).orElseThrow());
    }
}
