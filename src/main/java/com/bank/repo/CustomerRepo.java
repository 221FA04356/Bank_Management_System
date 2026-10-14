package com.bank.repo;

import com.bank.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public interface CustomerRepo extends JpaRepository<Customer, Long> {
    Optional<Customer> findByCardNumber(String card);
    Optional<Customer> findByAccountNumber(String acc);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPan(String pan);
    boolean existsByAadhar(String aadhar);
    boolean existsByNameIgnoreCaseAndDob(String name, String dob);
    boolean existsByCardNumber(String card);
    boolean existsByAccountNumber(String acc);
    List<Customer> findByRoleOrderByIdDesc(String role);
    List<Customer> findByRoleAndAccountTypeIgnoreCase(String role, String type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> lockById(@Param("id") Long id);

    @Query("select coalesce(sum(c.balance),0) from Customer c where c.role = 'USER'")
    BigDecimal totalBalance();
}
