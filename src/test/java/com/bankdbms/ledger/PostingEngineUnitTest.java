package com.bankdbms.ledger;

import com.bankdbms.exception.ValidationException;
import com.bankdbms.model.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostingEngineUnitTest {

    @Test
    @DisplayName("TC-LED-01 unbalanced postings are rejected before touching the database")
    void rejectsUnbalanced() {
        PostingRequest unbalanced = PostingRequest.of(TransactionType.DEPOSIT, "x",
                PostingLine.debit(1, new BigDecimal("100.00"), BalanceRule.NON_NEGATIVE),
                PostingLine.credit(10, new BigDecimal("99.99")));
        assertThrows(IllegalArgumentException.class, () -> PostingEngine.requireBalanced(unbalanced));
    }

    @Test
    @DisplayName("TC-LED-02 a posting needs at least two lines and positive amounts")
    void rejectsSingleLineAndZero() {
        assertThrows(IllegalArgumentException.class, () -> PostingEngine.requireBalanced(PostingRequest.of(
                TransactionType.DEPOSIT, "x", PostingLine.credit(10, new BigDecimal("1.00")))));
        assertThrows(ValidationException.class, () -> PostingEngine.requireBalanced(PostingRequest.of(
                TransactionType.DEPOSIT, "x", PostingLine.debit(1, BigDecimal.ZERO, BalanceRule.NON_NEGATIVE),
                PostingLine.credit(10, BigDecimal.ZERO))));
    }

    @Test
    @DisplayName("TC-LED-03 a three-line balanced posting (EMI split) is accepted")
    void acceptsSplit() {
        assertDoesNotThrow(() -> PostingEngine.requireBalanced(PostingRequest.of(TransactionType.LOAN_REPAYMENT, "x",
                PostingLine.debit(10, new BigDecimal("8884.88"), BalanceRule.MIN_BALANCE),
                PostingLine.credit(2, new BigDecimal("7884.88")),
                PostingLine.credit(4, new BigDecimal("1000.00")))));
    }
}
