package com.bankdbms.model;

import java.math.BigDecimal;

public record ChargeType(int chargeTypeId, String chargeCode, String chargeName, BigDecimal defaultAmount) {
}
