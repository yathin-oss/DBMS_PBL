package com.bankdbms.model;

import java.time.LocalDate;

/** Operator input for registering or updating a customer (validated by CustomerService). */
public record CustomerForm(String firstName, String lastName, LocalDate dateOfBirth, Gender gender, String email,
                           String phone, String address, String city, IdProofType idProofType,
                           String idProofNumber) {
}
