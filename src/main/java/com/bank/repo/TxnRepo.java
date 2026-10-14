package com.bank.repo;

import com.bank.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public interface TxnRepo extends JpaRepository<Txn, Long> {
    List<Txn> findTop10ByCustomerIdOrderByIdDesc(Long id);
    List<Txn> findTop15ByOrderByIdDesc();
    List<Txn> findTop20ByCustomerIdAndTypeInOrderByIdDesc(Long id, Collection<String> types);
    List<Txn> findByCustomerIdAndCreatedAtBetweenOrderByIdDesc(Long id, LocalDateTime a, LocalDateTime b);
    List<Txn> findByCustomerIdAndCreatedAtAfter(Long id, LocalDateTime a);
    long countByCustomerIdAndTypeInAndCreatedAtAfter(Long id, Collection<String> types, LocalDateTime a);

    @Query("select coalesce(sum(t.amount),0) from Txn t where t.customerId = :id and t.type in :types and t.createdAt >= :since")
    BigDecimal sumSince(@Param("id") Long id, @Param("types") Collection<String> types, @Param("since") LocalDateTime since);
}
