package com.volunteer.vms.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByUsername(String username);

    Optional<User> findByUsername(String username);

    List<User> findTop20ByOrderByPointsDescCreatedAtAsc();

    @Query("select u.id from User u")
    List<Long> findAllIds();
}
