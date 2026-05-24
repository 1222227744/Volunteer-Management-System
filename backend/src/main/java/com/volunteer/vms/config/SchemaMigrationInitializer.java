package com.volunteer.vms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 轻量数据库迁移器：
 * 1. 将 v2 阶段的重要结构调整整理为可追踪的版本脚本；
 * 2. 对已有数据库先做兼容判定，避免重复执行相同脚本；
 * 3. 对仍无法自动修复的数据冲突保留告警，而不是在启动期强行破坏数据。
 */
@Component
@Order(0)
public class SchemaMigrationInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationInitializer.class);
    private static final String HISTORY_TABLE = "schema_migrations";

    private static final List<MigrationDefinition> MIGRATIONS = List.of(
            new MigrationDefinition(
                    "V2_001",
                    "补充活动报名签退字段",
                    "sql/migrations/V2_001__add_check_out_at_to_activity_registrations.sql"
            ),
            new MigrationDefinition(
                    "V2_002",
                    "兼容旧版活动报名状态枚举",
                    "sql/migrations/V2_002__normalize_activity_registration_status_enum.sql"
            ),
            new MigrationDefinition(
                    "V2_003",
                    "回填旧版 REGISTERED 报名状态",
                    "sql/migrations/V2_003__backfill_registered_status_to_approved.sql"
            ),
            new MigrationDefinition(
                    "V2_004",
                    "广播通知拆分为用户级通知",
                    "sql/migrations/V2_004__migrate_broadcast_notifications_to_user_scope.sql"
            ),
            new MigrationDefinition(
                    "V2_005",
                    "补充服务记录唯一约束",
                    "sql/migrations/V2_005__add_service_record_unique_constraint.sql"
            ),
            new MigrationDefinition(
                    "V2_006",
                    "补充报名活动状态联合索引",
                    "sql/migrations/V2_006__add_registration_activity_status_index.sql"
            ),
            new MigrationDefinition(
                    "V2_007",
                    "补充通知用户已读索引",
                    "sql/migrations/V2_007__add_notification_user_read_created_index.sql"
            ),
            new MigrationDefinition(
                    "V2_008",
                    "补充服务记录用户时间索引",
                    "sql/migrations/V2_008__add_service_record_user_created_index.sql"
            ),
            new MigrationDefinition(
                    "V2_009",
                    "补充审计日志时间索引",
                    "sql/migrations/V2_009__add_audit_log_created_index.sql"
            ),
            new MigrationDefinition(
                    "V2_010",
                    "补充审计日志动作时间索引",
                    "sql/migrations/V2_010__add_audit_log_action_created_index.sql"
            ),
            new MigrationDefinition(
                    "V3_001",
                    "补充活动报名截止时间字段",
                    "sql/migrations/V3_001__add_activity_registration_deadline.sql"
            ),
            new MigrationDefinition(
                    "V3_002",
                    "补充活动参与要求字段",
                    "sql/migrations/V3_002__add_activity_participation_requirement.sql"
            ),
            new MigrationDefinition(
                    "V3_003",
                    "补充报名审核说明字段",
                    "sql/migrations/V3_003__add_registration_review_comment.sql"
            ),
            new MigrationDefinition(
                    "V3_004",
                    "补充报名审核时间字段",
                    "sql/migrations/V3_004__add_registration_reviewed_at.sql"
            )
    );

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public SchemaMigrationInitializer(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        ensureMigrationHistoryTable();
        for (MigrationDefinition migration : MIGRATIONS) {
            applyMigration(migration);
        }
    }

    private void applyMigration(MigrationDefinition migration) {
        if (migrationRecorded(migration.version())) {
            return;
        }
        MigrationDecision decision = decideMigration(migration.version());
        switch (decision.action()) {
            case EXECUTE -> {
                executeScript(migration.scriptPath());
                recordMigration(migration, "EXECUTED", decision.reason());
                log.info("已执行数据库迁移 {} - {}", migration.version(), migration.description());
            }
            case BASELINE -> {
                recordMigration(migration, "BASELINED", decision.reason());
                log.info("迁移 {} 已满足目标结构，按基线登记: {}", migration.version(), decision.reason());
            }
            case BLOCKED -> log.warn("迁移 {} 暂未执行: {}", migration.version(), decision.reason());
        }
    }

    private MigrationDecision decideMigration(String version) {
        return switch (version) {
            case "V2_001" -> decideCheckOutColumnMigration();
            case "V2_002" -> decideRegistrationEnumMigration();
            case "V2_003" -> decideRegisteredStatusBackfill();
            case "V2_004" -> decideBroadcastNotificationMigration();
            case "V2_005" -> decideServiceRecordUniqueConstraint();
            case "V2_006" -> decideIndexMigration("activity_registrations", "idx_registrations_activity_status");
            case "V2_007" -> decideIndexMigration("notifications", "idx_notifications_user_read_created_at");
            case "V2_008" -> decideIndexMigration("service_records", "idx_service_records_user_created_at");
            case "V2_009" -> decideIndexMigration("audit_logs", "idx_audit_logs_created_at");
            case "V2_010" -> decideIndexMigration("audit_logs", "idx_audit_logs_action_created_at");
            case "V3_001" -> decideColumnMigration("activities", "registration_deadline");
            case "V3_002" -> decideColumnMigration("activities", "participation_requirement");
            case "V3_003" -> decideColumnMigration("activity_registrations", "review_comment");
            case "V3_004" -> decideColumnMigration("activity_registrations", "reviewed_at");
            default -> new MigrationDecision(MigrationAction.EXECUTE, "未提供兼容判定，按脚本执行");
        };
    }

    private MigrationDecision decideCheckOutColumnMigration() {
        if (!tableExists("activity_registrations")) {
            return baseline("activity_registrations 表尚不存在");
        }
        if (columnExists("activity_registrations", "check_out_at")) {
            return baseline("check_out_at 字段已存在");
        }
        return execute("缺少签退时间字段");
    }

    private MigrationDecision decideRegistrationEnumMigration() {
        if (!tableExists("activity_registrations")) {
            return baseline("activity_registrations 表尚不存在");
        }
        String dataType = queryForString(
                """
                SELECT DATA_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = 'status'
                """,
                "activity_registrations"
        );
        if (dataType == null || !"enum".equalsIgnoreCase(dataType)) {
            return baseline("status 列不是 MySQL ENUM，无需兼容脚本");
        }
        String columnType = queryForString(
                """
                SELECT COLUMN_TYPE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = 'status'
                """,
                "activity_registrations"
        );
        if (columnType == null) {
            return baseline("未读取到 status 列定义");
        }
        String normalized = columnType.toUpperCase(Locale.ROOT);
        boolean alreadyTarget = normalized.contains("'PENDING'")
                && normalized.contains("'APPROVED'")
                && normalized.contains("'REJECTED'")
                && normalized.contains("'CHECKED_OUT'")
                && !normalized.contains("'REGISTERED'");
        if (alreadyTarget) {
            return baseline("报名状态枚举已是目标定义");
        }
        return execute("旧版报名状态枚举仍需扩展并回填");
    }

    private MigrationDecision decideRegisteredStatusBackfill() {
        if (!tableExists("activity_registrations")) {
            return baseline("activity_registrations 表尚不存在");
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_registrations WHERE status = 'REGISTERED'",
                Integer.class
        );
        if (count == null || count == 0) {
            return baseline("不存在 REGISTERED 旧状态数据");
        }
        return execute("仍有 REGISTERED 旧状态数据需要回填");
    }

    private MigrationDecision decideBroadcastNotificationMigration() {
        if (!tableExists("notifications")) {
            return baseline("notifications 表尚不存在");
        }
        Integer broadcastCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id IS NULL",
                Integer.class
        );
        if (broadcastCount == null || broadcastCount == 0) {
            return baseline("不存在 user_id 为空的广播通知");
        }
        Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        if (userCount == null || userCount == 0) {
            return blocked("当前没有用户数据，无法拆分广播通知");
        }
        return execute("仍存在共享广播通知记录");
    }

    private MigrationDecision decideServiceRecordUniqueConstraint() {
        if (!tableExists("service_records")) {
            return baseline("service_records 表尚不存在");
        }
        if (indexExists("service_records", "uk_service_record_activity_user")) {
            return baseline("activity_id + user_id 唯一约束已存在");
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
            return blocked("service_records 存在重复 activity_id + user_id 数据，请先人工清理");
        }
        return execute("服务记录尚未收紧为一活动一志愿者一条记录");
    }

    private MigrationDecision decideIndexMigration(String tableName, String indexName) {
        if (!tableExists(tableName)) {
            return baseline(tableName + " 表尚不存在");
        }
        if (indexExists(tableName, indexName)) {
            return baseline(indexName + " 索引已存在");
        }
        return execute("缺少 " + indexName + " 索引");
    }

    private MigrationDecision decideColumnMigration(String tableName, String columnName) {
        if (!tableExists(tableName)) {
            return baseline(tableName + " 表尚不存在");
        }
        if (columnExists(tableName, columnName)) {
            return baseline(columnName + " 字段已存在");
        }
        return execute("缺少 " + columnName + " 字段");
    }

    private void ensureMigrationHistoryTable() {
        jdbcTemplate.execute(
                """
                CREATE TABLE IF NOT EXISTS schema_migrations (
                  version VARCHAR(64) PRIMARY KEY,
                  description VARCHAR(255) NOT NULL,
                  script_path VARCHAR(255) NOT NULL,
                  checksum VARCHAR(64) NOT NULL,
                  apply_mode VARCHAR(20) NOT NULL,
                  note VARCHAR(500) NULL,
                  installed_at DATETIME NOT NULL
                )
                """
        );
    }

    private boolean migrationRecorded(String version) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + HISTORY_TABLE + " WHERE version = ?",
                Integer.class,
                version
        );
        return count != null && count > 0;
    }

    private void recordMigration(MigrationDefinition migration, String applyMode, String note) {
        jdbcTemplate.update(
                """
                INSERT INTO schema_migrations
                  (version, description, script_path, checksum, apply_mode, note, installed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                migration.version(),
                migration.description(),
                migration.scriptPath(),
                checksum(migration.scriptPath()),
                applyMode,
                shorten(note, 500),
                LocalDateTime.now()
        );
    }

    private void executeScript(String scriptPath) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setContinueOnError(false);
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(new ClassPathResource(scriptPath));
        DatabasePopulatorUtils.execute(populator, dataSource);
    }

    private String checksum(String scriptPath) {
        try (InputStream inputStream = new ClassPathResource(scriptPath).getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("无法计算迁移脚本校验和: " + scriptPath, ex);
        }
    }

    private String shorten(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String queryForString(String sql, Object... args) {
        List<String> values = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString(1), args);
        return values.isEmpty() ? null : values.get(0);
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

    private MigrationDecision execute(String reason) {
        return new MigrationDecision(MigrationAction.EXECUTE, reason);
    }

    private MigrationDecision baseline(String reason) {
        return new MigrationDecision(MigrationAction.BASELINE, reason);
    }

    private MigrationDecision blocked(String reason) {
        return new MigrationDecision(MigrationAction.BLOCKED, reason);
    }

    private record MigrationDefinition(String version, String description, String scriptPath) {
    }

    private record MigrationDecision(MigrationAction action, String reason) {
    }

    private enum MigrationAction {
        EXECUTE,
        BASELINE,
        BLOCKED
    }
}
