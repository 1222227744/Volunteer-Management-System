package com.volunteer.vms.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExternalNotificationTaskRepository extends JpaRepository<ExternalNotificationTask, Long> {
    List<ExternalNotificationTask> findAllByOrderByCreatedAtDesc();

    List<ExternalNotificationTask> findByStatusOrderByCreatedAtAsc(ExternalNotificationStatus status);
}
