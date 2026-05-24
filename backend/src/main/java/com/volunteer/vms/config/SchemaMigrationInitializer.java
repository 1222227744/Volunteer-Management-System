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
            ),
            new MigrationDefinition(
                    "V3_005",
                    "补充用户联系电话字段",
                    "sql/migrations/V3_005__add_user_phone.sql"
            ),
            new MigrationDefinition(
                    "V3_006",
                    "补充用户服务意向字段",
                    "sql/migrations/V3_006__add_user_service_intention.sql"
            ),
            new MigrationDefinition(
                    "V3_007",
                    "补充用户账号状态字段",
                    "sql/migrations/V3_007__add_user_account_status.sql"
            ),
            new MigrationDefinition(
                    "V3_008",
                    "补充用户实名审核状态字段",
                    "sql/migrations/V3_008__add_user_verification_status.sql"
            ),
            new MigrationDefinition(
                    "V3_009",
                    "补充用户实名审核说明字段",
                    "sql/migrations/V3_009__add_user_verification_comment.sql"
            ),
            new MigrationDefinition(
                    "V3_010",
                    "创建公益资源表",
                    "sql/migrations/V3_010__create_public_resources.sql"
            ),
            new MigrationDefinition(
                    "V3_011",
                    "创建帮扶需求表",
                    "sql/migrations/V3_011__create_help_needs.sql"
            ),
            new MigrationDefinition(
                    "V3_012",
                    "创建资源匹配表",
                    "sql/migrations/V3_012__create_resource_matches.sql"
            ),
            new MigrationDefinition(
                    "V3_013",
                    "补充资源对接查询索引",
                    "sql/migrations/V3_013__add_resource_indexes.sql"
            ),
            new MigrationDefinition(
                    "V3_014",
                    "补充捐赠记录订单关联字段",
                    "sql/migrations/V3_014__add_donation_order_id.sql"
            ),
            new MigrationDefinition(
                    "V3_015",
                    "创建捐赠订单表",
                    "sql/migrations/V3_015__create_donation_orders.sql"
            ),
            new MigrationDefinition(
                    "V3_016",
                    "补充捐赠订单用户时间索引",
                    "sql/migrations/V3_016__add_donation_order_indexes.sql"
            ),
            new MigrationDefinition(
                    "V3_017",
                    "补充捐赠订单状态时间索引",
                    "sql/migrations/V3_017__add_donation_order_status_index.sql"
            ),
            new MigrationDefinition(
                    "V3_018",
                    "创建文件审计表",
                    "sql/migrations/V3_018__create_file_assets.sql"
            ),
            new MigrationDefinition(
                    "V3_019",
                    "补充活动附件字段",
                    "sql/migrations/V3_019__add_activity_attachment_file_id.sql"
            ),
            new MigrationDefinition(
                    "V3_020",
                    "补充内容图片字段",
                    "sql/migrations/V3_020__add_content_image_file_id.sql"
            ),
            new MigrationDefinition(
                    "V3_021",
                    "补充服务证明文件字段",
                    "sql/migrations/V3_021__add_service_record_evidence_file_id.sql"
            ),
            new MigrationDefinition(
                    "V3_022",
                    "补充文件业务关联索引",
                    "sql/migrations/V3_022__add_file_asset_business_index.sql"
            ),
            new MigrationDefinition(
                    "V3_023",
                    "补充文件上传人索引",
                    "sql/migrations/V3_023__add_file_asset_uploader_index.sql"
            ),
            new MigrationDefinition(
                    "V3_024",
                    "创建荣誉激励记录表",
                    "sql/migrations/V3_024__create_honor_records.sql"
            ),
            new MigrationDefinition(
                    "V3_025",
                    "补充荣誉用户时间索引",
                    "sql/migrations/V3_025__add_honor_user_index.sql"
            ),
            new MigrationDefinition(
                    "V3_026",
                    "补充公开荣誉时间索引",
                    "sql/migrations/V3_026__add_honor_public_index.sql"
            ),
            new MigrationDefinition(
                    "V3_027",
                    "创建外部通知任务表",
                    "sql/migrations/V3_027__create_external_notification_tasks.sql"
            ),
            new MigrationDefinition(
                    "V3_028",
                    "补充外部通知状态时间索引",
                    "sql/migrations/V3_028__add_external_notification_status_index.sql"
            ),
            new MigrationDefinition(
                    "V3_029",
                    "补充外部通知用户时间索引",
                    "sql/migrations/V3_029__add_external_notification_user_index.sql"
            ),
            new MigrationDefinition(
                    "V3_030",
                    "补充活动签到码字段",
                    "sql/migrations/V3_030__add_activity_check_code.sql"
            ),
            new MigrationDefinition(
                    "V3_031",
                    "创建活动考勤更正记录表",
                    "sql/migrations/V3_031__create_activity_attendance_corrections.sql"
            ),
            new MigrationDefinition(
                    "V3_032",
                    "补充活动考勤更正查询索引",
                    "sql/migrations/V3_032__add_attendance_correction_indexes.sql"
            ),
            new MigrationDefinition(
                    "V3_033",
                    "规范化活动签到码数据",
                    "sql/migrations/V3_033__normalize_activity_check_code.sql"
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
            case "V3_005" -> decideColumnMigration("users", "phone");
            case "V3_006" -> decideColumnMigration("users", "service_intention");
            case "V3_007" -> decideColumnMigration("users", "account_status");
            case "V3_008" -> decideColumnMigration("users", "verification_status");
            case "V3_009" -> decideColumnMigration("users", "verification_comment");
            case "V3_010" -> decideTableMigration("public_resources");
            case "V3_011" -> decideTableMigration("help_needs");
            case "V3_012" -> decideTableMigration("resource_matches");
            case "V3_013" -> decideResourceIndexMigration();
            case "V3_014" -> decideColumnMigration("donations", "order_id");
            case "V3_015" -> decideTableMigration("donation_orders");
            case "V3_016" -> decideIndexMigration("donation_orders", "idx_donation_orders_user_created_at");
            case "V3_017" -> decideIndexMigration("donation_orders", "idx_donation_orders_status_created_at");
            case "V3_018" -> decideTableMigration("file_assets");
            case "V3_019" -> decideColumnMigration("activities", "attachment_file_id");
            case "V3_020" -> decideColumnMigration("content_posts", "image_file_id");
            case "V3_021" -> decideColumnMigration("service_records", "evidence_file_id");
            case "V3_022" -> decideIndexMigration("file_assets", "idx_file_assets_business");
            case "V3_023" -> decideIndexMigration("file_assets", "idx_file_assets_uploader_created_at");
            case "V3_024" -> decideTableMigration("honor_records");
            case "V3_025" -> decideIndexMigration("honor_records", "idx_honor_records_user_awarded_at");
            case "V3_026" -> decideIndexMigration("honor_records", "idx_honor_records_public_awarded_at");
            case "V3_027" -> decideTableMigration("external_notification_tasks");
            case "V3_028" -> decideIndexMigration("external_notification_tasks", "idx_external_notification_tasks_status_created_at");
            case "V3_029" -> decideIndexMigration("external_notification_tasks", "idx_external_notification_tasks_user_created_at");
            case "V3_030" -> decideColumnMigration("activities", "check_code");
            case "V3_031" -> decideTableMigration("activity_attendance_corrections");
            case "V3_032" -> decideAttendanceCorrectionIndexMigration();
            case "V3_033" -> decideActivityCheckCodeNormalizeMigration();
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

    private MigrationDecision decideTableMigration(String tableName) {
        if (tableExists(tableName)) {
            return baseline(tableName + " 表已存在");
        }
        return execute("缺少 " + tableName + " 表");
    }

    private MigrationDecision decideResourceIndexMigration() {
        if (!tableExists("public_resources") || !tableExists("help_needs") || !tableExists("resource_matches")) {
            return baseline("资源对接相关表尚未全部存在");
        }
        if (indexExists("public_resources", "idx_public_resources_status_created_at")
                && indexExists("help_needs", "idx_help_needs_status_created_at")
                && indexExists("resource_matches", "idx_resource_matches_status_created_at")) {
            return baseline("资源对接索引已存在");
        }
        return execute("缺少资源对接查询索引");
    }

    private MigrationDecision decideAttendanceCorrectionIndexMigration() {
        if (!tableExists("activity_attendance_corrections")) {
            return baseline("activity_attendance_corrections 表尚不存在");
        }
        if (indexExists("activity_attendance_corrections", "idx_attendance_corrections_activity_corrected")
                && indexExists("activity_attendance_corrections", "idx_attendance_corrections_registration_corrected")) {
            return baseline("活动考勤更正索引已存在");
        }
        return execute("缺少活动考勤更正查询索引");
    }

    private MigrationDecision decideActivityCheckCodeNormalizeMigration() {
        if (!tableExists("activities")) {
            return baseline("activities 表尚不存在");
        }
        if (!columnExists("activities", "check_code")) {
            return blocked("activities.check_code 字段尚不存在，请先执行 V3_030");
        }
        Integer missingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activities WHERE check_code IS NULL OR check_code = ''",
                Integer.class
        );
        String nullable = queryForString(
                """
                SELECT IS_NULLABLE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'activities'
                  AND COLUMN_NAME = 'check_code'
                """
        );
        if ((missingCount == null || missingCount == 0) && "NO".equalsIgnoreCase(nullable)) {
            return baseline("活动签到码已回填且字段非空");
        }
        return execute("活动签到码存在空值或字段仍允许为空");
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
