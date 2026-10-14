package com.bank.repo;

import com.bank.model.Notification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface NotificationRepo extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByCustomerIdAndTypeOrderByIdDesc(Long customerId, String type);
    List<Notification> findTop50ByCustomerIdAndTypeNotOrderByIdDesc(Long customerId, String type);
    long countByCustomerIdAndSeenFalse(Long customerId);

    @Modifying @Transactional
    @Query("update Notification n set n.seen = true where n.customerId = :id and n.seen = false")
    int markAllSeen(@Param("id") Long id);
}
