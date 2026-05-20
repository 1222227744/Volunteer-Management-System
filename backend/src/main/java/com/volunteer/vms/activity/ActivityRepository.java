package com.volunteer.vms.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findAllByOrderByStartTimeDesc();

    long countByOrganizerId(Long organizerId);

    List<Activity> findByOrganizerIdOrderByStartTimeDesc(Long organizerId);

    @Query("select a.id from Activity a where a.organizerId = ?1")
    List<Long> findIdsByOrganizerId(Long organizerId);
}
