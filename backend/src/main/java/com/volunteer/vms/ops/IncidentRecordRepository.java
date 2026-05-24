package com.volunteer.vms.ops;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentRecordRepository extends JpaRepository<IncidentRecord, Long> {
    List<IncidentRecord> findAllByOrderByCreatedAtDesc();

    long countByStatus(IncidentStatus status);
}
