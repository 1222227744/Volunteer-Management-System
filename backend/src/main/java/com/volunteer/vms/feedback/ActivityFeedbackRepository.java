package com.volunteer.vms.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityFeedbackRepository extends JpaRepository<ActivityFeedback, Long> {
    boolean existsByActivityIdAndUserId(Long activityId, Long userId);

    Optional<ActivityFeedback> findByActivityIdAndUserId(Long activityId, Long userId);

    List<ActivityFeedback> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ActivityFeedback> findByActivityIdOrderByCreatedAtDesc(Long activityId);
}
