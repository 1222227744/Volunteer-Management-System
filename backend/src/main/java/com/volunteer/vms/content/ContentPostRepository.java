package com.volunteer.vms.content;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContentPostRepository extends JpaRepository<ContentPost, Long> {
    List<ContentPost> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ContentPost> findByStatusOrderByCreatedAtDesc(ContentStatus status);

    long countByStatus(ContentStatus status);
}
