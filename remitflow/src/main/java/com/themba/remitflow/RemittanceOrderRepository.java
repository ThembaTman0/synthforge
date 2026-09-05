package com.themba.remitflow;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RemittanceOrderRepository extends JpaRepository<RemittanceOrder, Long> {
}
