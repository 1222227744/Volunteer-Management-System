package com.volunteer.vms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 兼容已有数据库结构。
 * Hibernate 的 ddl-auto=update 不会自动扩展 MySQL ENUM 值，因此这里补一层启动迁移。
 */
@Component
@Order(0)
public class SchemaMigrationInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationInitializer.class);

    private static final String REGISTRATION_STATUS_ENUM_COMPAT =
            "ENUM('REGISTERED','PENDING','APPROVED','REJECTED','CHECKED_IN','CHECKED_OUT','COMPLETED','CANCELLED')";
    private static final String REGISTRATION_STATUS_ENUM_TARGET =
            "ENUM('PENDING','APPROVED','REJECTED','CHECKED_IN','CHECKED_OUT','COMPLETED','CANCELLED')";

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrationInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        if (tableExists("activity_registrations")) {
            ensureCheckOutColumn();
            migrateRegistrationStatusEnum();
        }
        if (tableExists("notifications")) {
            migrateBroadcastNotifications();
        }
        if (tableExists("service_records")) {
            ensureServiceRecordUniqueConstraint();
        }
    }

    private void ensureCheckOutColumn() {
        if (columnExists("activity_registrations", "check_out_at")) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE activity_registrations ADD COLUMN check_out_at DATETIME(6) NULL");
        log.info("已为 activity_registrations 表补充 check_out_at 字段");
    }

    private void migrateRegistrationStatusEnum() {
        String dataType = jdbcTemplate.queryForObject(
                """
                SELECT DATA_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = 'status'
                """,
                String.class,
                "activity_registrations"
        );
        if (dataType == null || !"enum".equalsIgnoreCase(dataType)) {
            return;
        }

        String columnType = jdbcTemplate.queryForObject(
                """
                SELECT COLUMN_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = 'status'
                """,
                String.class,
                "activity_registrations"
        );
        if (columnType == null) {
            return;
        }

        String normalized = columnType.toUpperCase(Locale.ROOT);
        boolean alreadyTarget = normalized.contains("'PENDING'")
                && normalized.contains("'APPROVED'")
                && normalized.contains("'REJECTED'")
                && normalized.contains("'CHECKED_OUT'")
                && !normalized.contains("'REGISTERED'");
        if (alreadyTarget) {
            return;
        }

        jdbcTemplate.execute(
                "ALTER TABLE activity_registrations MODIFY COLUMN status " + REGISTRATION_STATUS_ENUM_COMPAT + " NOT NULL"
        );
        jdbcTemplate.update(
                "UPDATE activity_registrations SET status = 'APPROVED' WHERE status = 'REGISTERED'"
        );
        jdbcTemplate.execute(
                "ALTER TABLE activity_registrations MODIFY COLUMN status " + REGISTRATION_STATUS_ENUM_TARGET + " NOT NULL"
        );
        log.info("已完成 activity_registrations.status 枚举迁移: REGISTERED -> APPROVED，并补齐新流程状态");
    }

    private void migrateBroadcastNotifications() {
        Integer broadcastCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id IS NULL",
                Integer.class
        );
        if (broadcastCount == null || broadcastCount == 0) {
            return;
        }
        java.util.List<Long> userIds = jdbcTemplate.queryForList("SELECT id FROM users", Long.class);
        if (userIds.isEmpty()) {
            return;
        }
        java.util.List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, title, content, created_at FROM notifications WHERE user_id IS NULL"
        );
        for (java.util.Map<String, Object> row : rows) {
            for (Long userId : userIds) {
                jdbcTemplate.update(
                        "INSERT INTO notifications (user_id, title, content, read_flag, created_at) VALUES (?, ?, ?, ?, ?)",
                        userId,
                        row.get("title"),
                        row.get("content"),
                        false,
                        row.get("created_at")
                );
            }
        }
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id IS NULL");
        log.info("已将 notifications 表中的广播通知迁移为用户级独立通知记录");
    }

    private void ensureServiceRecordUniqueConstraint() {
        if (indexExists("service_records", "uk_service_record_activity_user")) {
            return;
        }
        Integer duplicates = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM (
                    SELECT activity_id, user_id, COUNT(*) AS cnt
                    FROM service_records
                    GROUP BY activity_id, user_id
                    HAVING COUNT(*) > 1
                ) t
                """,
                Integer.class
        );
        if (duplicates != null && duplicates > 0) {
            log.warn("service_records 存在重复 activity_id + user_id 数据，跳过唯一约束补齐，请先人工清理");
            return;
        }
        jdbcTemplate.execute(
                "ALTER TABLE service_records ADD CONSTRAINT uk_service_record_activity_user UNIQUE (activity_id, user_id)"
        );
        log.info("已为 service_records 表补充 activity_id + user_id 唯一约束");
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """,
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """,
                Integer.class,
                tableName,
                indexName
        );
        return count != null && count > 0;
    }
}
