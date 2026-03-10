package com.volunteer.vms.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrUserIdIsNullOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadFlagFalse(Long userId);
}
