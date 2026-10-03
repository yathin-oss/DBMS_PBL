package com.bankdbms.ledger;

import com.bankdbms.model.TransactionType;

import java.util.List;

/** A complete financial event: type, narration, optional reversed transaction, and its lines. */
public record PostingRequest(TransactionType type, String narration, Long reversalOfId, List<PostingLine> lines) {

    public PostingRequest {
        lines = List.copyOf(lines);
    }

    public static PostingRequest of(TransactionType type, String narration, PostingLine... lines) {
        return new PostingRequest(type, narration, null, List.of(lines));
    }
}
