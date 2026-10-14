package com.bank.repo;

import com.bank.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public interface GoalRepo extends JpaRepository<Goal, Long> {
    List<Goal> findByOwnerId(Long ownerId);
}
