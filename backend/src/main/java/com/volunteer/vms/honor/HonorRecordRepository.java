package com.volunteer.vms.honor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HonorRecordRepository extends JpaRepository<HonorRecord, Long> {
    List<HonorRecord> findByUserIdOrderByAwardedAtDesc(Long userId);

    List<HonorRecord> findByPublicVisibleTrueOrderByAwardedAtDesc();

    List<HonorRecord> findAllByOrderByAwardedAtDesc();
}
