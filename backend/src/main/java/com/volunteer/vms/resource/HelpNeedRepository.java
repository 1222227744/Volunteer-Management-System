package com.volunteer.vms.resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HelpNeedRepository extends JpaRepository<HelpNeed, Long> {
    List<HelpNeed> findAllByOrderByCreatedAtDesc();

    long countByStatus(NeedStatus status);
}
