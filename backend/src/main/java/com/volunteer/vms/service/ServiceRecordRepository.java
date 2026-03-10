package com.volunteer.vms.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface ServiceRecordRepository extends JpaRepository<ServiceRecord, Long> {
    List<ServiceRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("select coalesce(sum(sr.hours), 0) from ServiceRecord sr where sr.userId = ?1")
    BigDecimal sumHoursByUserId(Long userId);

    @Query("select coalesce(sum(sr.hours), 0) from ServiceRecord sr")
    BigDecimal sumHoursAll();
}
