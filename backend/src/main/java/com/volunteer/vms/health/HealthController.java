package com.volunteer.vms.health;

import com.volunteer.vms.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        Integer databaseProbe = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return ApiResponse.success(Map.of(
                "status", "UP",
                "database", databaseProbe != null && databaseProbe == 1 ? "UP" : "UNKNOWN",
                "time", LocalDateTime.now()
        ));
    }
}
