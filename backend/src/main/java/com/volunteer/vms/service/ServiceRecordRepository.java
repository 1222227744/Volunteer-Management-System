package com.volunteer.vms.service;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

public interface ServiceRecordRepository extends JpaRepository<ServiceRecord, Long> {
    List<ServiceRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ServiceRecord> findByUserIdAndActivityIdOrderByCreatedAtDesc(Long userId, Long activityId);

    boolean existsByActivityIdAndUserId(Long activityId, Long userId);

    @Query("select coalesce(sum(sr.hours), 0) from ServiceRecord sr where sr.userId = ?1")
    BigDecimal sumHoursByUserId(Long userId);

    @Query("select coalesce(sum(sr.hours), 0) from ServiceRecord sr where sr.userId = ?1 and sr.activityId = ?2")
    BigDecimal sumHoursByUserIdAndActivityId(Long userId, Long activityId);

    @Query("select coalesce(sum(sr.hours), 0) from ServiceRecord sr")
    BigDecimal sumHoursAll();

    @Query("select coalesce(sum(sr.hours), 0) from ServiceRecord sr where sr.activityId in ?1")
    BigDecimal sumHoursByActivityIds(Collection<Long> activityIds);
}
