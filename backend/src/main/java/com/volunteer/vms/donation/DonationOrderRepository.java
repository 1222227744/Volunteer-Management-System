package com.volunteer.vms.donation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DonationOrderRepository extends JpaRepository<DonationOrder, Long> {
    List<DonationOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<DonationOrder> findAllByOrderByCreatedAtDesc();
}
