package com.bank.repo;

import com.bank.model.MoneyRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MoneyRequestRepo extends JpaRepository<MoneyRequest, Long> {
    List<MoneyRequest> findByRequesterIdOrderByIdDesc(Long id);
    List<MoneyRequest> findByPayerIdOrderByIdDesc(Long id);
}
