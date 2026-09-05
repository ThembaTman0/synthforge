package com.themba.remitflow;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RemittanceOrderRepository extends JpaRepository<RemittanceOrder, Long> {

    /** Backs GET /api/v1/orders?status= (remitflow-v1-spec.md section 7). */
    List<RemittanceOrder> findByStatus(OrderStatus status);
}
