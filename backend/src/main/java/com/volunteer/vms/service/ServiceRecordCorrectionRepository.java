package com.volunteer.vms.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ServiceRecordCorrectionRepository extends JpaRepository<ServiceRecordCorrection, Long> {
    boolean existsByServiceRecordIdAndStatus(Long serviceRecordId, ServiceRecordCorrectionStatus status);

    List<ServiceRecordCorrection> findByUserIdOrderByRequestedAtDesc(Long userId);

    List<ServiceRecordCorrection> findAllByOrderByRequestedAtDesc();

    List<ServiceRecordCorrection> findByActivityIdInOrderByRequestedAtDesc(Collection<Long> activityIds);
}
