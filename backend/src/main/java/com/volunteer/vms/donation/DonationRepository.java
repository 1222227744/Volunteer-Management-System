package com.volunteer.vms.donation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    List<Donation> findAllByOrderByCreatedAtDesc();

    List<Donation> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("select coalesce(sum(d.amount), 0) from Donation d")
    BigDecimal totalAmount();
}
