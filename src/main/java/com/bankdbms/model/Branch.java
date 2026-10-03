package com.bankdbms.model;

import java.time.LocalDateTime;

public record Branch(int branchId, String ifscCode, String branchName, String address, String city,
                     String phone, LocalDateTime createdAt) {
}
