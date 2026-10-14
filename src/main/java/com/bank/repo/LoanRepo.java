package com.bank.repo;

import com.bank.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public interface LoanRepo extends JpaRepository<Loan, Long> {
    List<Loan> findByOwnerIdOrderByIdDesc(Long ownerId);
    List<Loan> findByStatusOrderByIdAsc(String status);
}
