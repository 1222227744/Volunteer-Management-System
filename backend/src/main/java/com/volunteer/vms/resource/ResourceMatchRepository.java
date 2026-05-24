package com.volunteer.vms.resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResourceMatchRepository extends JpaRepository<ResourceMatch, Long> {
    List<ResourceMatch> findAllByOrderByCreatedAtDesc();
}
