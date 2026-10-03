package com.bankdbms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Customer(int customerId, String firstName, String lastName, LocalDate dateOfBirth, Gender gender,
                       String email, String phone, String address, String city, IdProofType idProofType,
                       String idProofNumber, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}
