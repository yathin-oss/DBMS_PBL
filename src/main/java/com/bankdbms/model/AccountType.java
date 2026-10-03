package com.bankdbms.model;

import java.math.BigDecimal;

/** A banking product (CUSTOMER) or a category of the bank's own accounts (INTERNAL). */
public record AccountType(int accountTypeId, String typeCode, String typeName, AccountCategory category,
                          EntrySide normalSide, BigDecimal minOpeningBalance, BigDecimal minBalance,
                          int maxHolders, BigDecimal interestRate, String description) {

    public boolean isCustomerProduct() {
        return category == AccountCategory.CUSTOMER;
    }
}
