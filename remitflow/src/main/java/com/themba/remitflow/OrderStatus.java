package com.themba.remitflow;

/**
 * RemittanceOrder lifecycle. Legal transitions are defined in
 * remitflow-v1-spec.md section 8 rule 5: CREATED -> SUBMITTED,
 * SUBMITTED -> SETTLED, CREATED -> REJECTED, SUBMITTED -> REJECTED.
 */
public enum OrderStatus {
    CREATED,
    SUBMITTED,
    SETTLED,
    REJECTED
}
