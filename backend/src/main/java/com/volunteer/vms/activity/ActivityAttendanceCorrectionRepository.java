package com.volunteer.vms.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ActivityAttendanceCorrectionRepository extends JpaRepository<ActivityAttendanceCorrection, Long> {
    List<ActivityAttendanceCorrection> findByActivityIdOrderByCorrectedAtDesc(Long activityId);

    List<ActivityAttendanceCorrection> findByRegistrationIdInOrderByCorrectedAtDesc(Collection<Long> registrationIds);
}
