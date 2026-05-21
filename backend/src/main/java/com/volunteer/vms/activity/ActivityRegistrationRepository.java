package com.volunteer.vms.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActivityRegistrationRepository extends JpaRepository<ActivityRegistration, Long> {
    boolean existsByActivityIdAndUserId(Long activityId, Long userId);

    long countByActivityIdAndStatusIn(Long activityId, Collection<RegistrationStatus> statuses);

    List<ActivityRegistration> findByUserIdOrderByRegisteredAtDesc(Long userId);

    Optional<ActivityRegistration> findByActivityIdAndUserId(Long activityId, Long userId);

    long countByStatus(RegistrationStatus status);

    List<ActivityRegistration> findByActivityIdOrderByRegisteredAtDesc(Long activityId);

    List<ActivityRegistration> findByActivityId(Long activityId);

    long countByActivityIdIn(Collection<Long> activityIds);

    long countByActivityIdInAndStatus(Collection<Long> activityIds, RegistrationStatus status);

    @Query("select count(distinct ar.userId) from ActivityRegistration ar where ar.activityId in ?1")
    long countDistinctUserIdByActivityIdIn(Collection<Long> activityIds);
}
