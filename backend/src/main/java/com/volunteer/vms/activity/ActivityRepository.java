package com.volunteer.vms.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
    List<Activity> findAllByOrderByStartTimeDesc();

    @Query("""
            select a from Activity a
            where (:status is null or a.status = :status)
              and (:keyword is null
                   or lower(a.title) like lower(concat('%', :keyword, '%'))
                   or lower(a.description) like lower(concat('%', :keyword, '%')))
              and (:location is null or lower(a.location) like lower(concat('%', :location, '%')))
              and (:startFrom is null or a.startTime >= :startFrom)
              and (:startTo is null or a.startTime <= :startTo)
            order by a.startTime desc
            """)
    List<Activity> search(@Param("status") ActivityStatus status,
                          @Param("keyword") String keyword,
                          @Param("location") String location,
                          @Param("startFrom") LocalDateTime startFrom,
                          @Param("startTo") LocalDateTime startTo);

    long countByOrganizerId(Long organizerId);

    List<Activity> findByOrganizerIdOrderByStartTimeDesc(Long organizerId);

    @Query("select a.id from Activity a where a.organizerId = ?1")
    List<Long> findIdsByOrganizerId(Long organizerId);
}
