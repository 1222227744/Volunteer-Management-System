package com.volunteer.vms.resource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicResourceRepository extends JpaRepository<PublicResource, Long> {
    List<PublicResource> findAllByOrderByCreatedAtDesc();

    long countByStatus(ResourceStatus status);
}
