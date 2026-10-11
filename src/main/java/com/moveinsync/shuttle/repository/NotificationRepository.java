package com.moveinsync.shuttle.repository;

import com.moveinsync.shuttle.domain.Notification;
import com.moveinsync.shuttle.domain.NotificationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByStatusOrderByIdAsc(NotificationStatus status);
    List<Notification> findTop100ByOrderByIdDesc();
    long countByStatus(NotificationStatus status);
}
