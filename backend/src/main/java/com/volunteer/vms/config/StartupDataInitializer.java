package com.volunteer.vms.config;

import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@Order(1)
@EnableConfigurationProperties(StartupDataInitializer.BootstrapProperties.class)
public class StartupDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupDataInitializer.class);
    private static final String DEFAULT_ADMIN_DISPLAY_NAME = "系统管理员";
    private static final String DEFAULT_ORGANIZER_DISPLAY_NAME = "组织方账号";
    private static final String LEGACY_MOJIBAKE_ADMIN_DISPLAY_NAME = mojibake(DEFAULT_ADMIN_DISPLAY_NAME);
    private static final String LEGACY_MOJIBAKE_ORGANIZER_DISPLAY_NAME = mojibake(DEFAULT_ORGANIZER_DISPLAY_NAME);
    private static final String LEGACY_GBK_MOJIBAKE_ADMIN_DISPLAY_NAME = gbkMojibake(DEFAULT_ADMIN_DISPLAY_NAME);
    private static final String LEGACY_GBK_MOJIBAKE_ORGANIZER_DISPLAY_NAME = gbkMojibake(DEFAULT_ORGANIZER_DISPLAY_NAME);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final BootstrapProperties bootstrapProperties;

    public StartupDataInitializer(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  JdbcTemplate jdbcTemplate,
                                  BootstrapProperties bootstrapProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.bootstrapProperties = bootstrapProperties;
    }

    @Override
    public void run(String... args) {
        repairUserDisplayName(bootstrapProperties.adminUsername(), safeDisplayName(bootstrapProperties.adminDisplayName(), DEFAULT_ADMIN_DISPLAY_NAME), Role.ADMIN);
        repairUserDisplayName(bootstrapProperties.organizerUsername(), safeDisplayName(bootstrapProperties.organizerDisplayName(), DEFAULT_ORGANIZER_DISPLAY_NAME), Role.ORGANIZER);
        repairUserDisplayName("admin@example.com", DEFAULT_ADMIN_DISPLAY_NAME, Role.ADMIN);
        repairUserDisplayName("organizer@example.com", DEFAULT_ORGANIZER_DISPLAY_NAME, Role.ORGANIZER);
        repairNameSnapshots();
        if (!bootstrapProperties.enabled()) {
            log.info("Skip bootstrap account creation because vms.bootstrap.enabled=false");
            return;
        }
        ensureUser(
                bootstrapProperties.adminUsername(),
                bootstrapProperties.adminPassword(),
                safeDisplayName(bootstrapProperties.adminDisplayName(), DEFAULT_ADMIN_DISPLAY_NAME),
                Role.ADMIN
        );
        ensureUser(
                bootstrapProperties.organizerUsername(),
                bootstrapProperties.organizerPassword(),
                safeDisplayName(bootstrapProperties.organizerDisplayName(), DEFAULT_ORGANIZER_DISPLAY_NAME),
                Role.ORGANIZER
        );
    }

    private void ensureUser(String username, String rawPassword, String displayName, Role role) {
        User existing = userRepository.findByUsername(username).orElse(null);
        if (existing != null) {
            repairDisplayNameIfNeeded(existing, username, displayName, role);
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setRole(role);
        userRepository.save(user);
        log.info("Bootstrap account created: username={}, role={}", username, role);
    }

    private void repairUserDisplayName(String username, String displayName, Role role) {
        User existing = userRepository.findByUsername(username).orElse(null);
        if (existing != null) {
            repairDisplayNameIfNeeded(existing, username, displayName, role);
        }
    }

    private void repairDisplayNameIfNeeded(User existing, String username, String displayName, Role role) {
        if (needsDisplayNameRepair(existing.getDisplayName(), role)) {
            existing.setDisplayName(displayName);
            userRepository.save(existing);
            log.info("Bootstrap display name repaired: username={}, role={}", username, role);
        }
    }

    private void repairNameSnapshots() {
        repairNameColumn("system_configs", "updated_by_name");
        repairNameColumn("incident_records", "created_by_name");
        repairNameColumn("audit_logs", "operator_name");
        repairNameColumn("activity_attendance_corrections", "corrected_by_name");
        repairNameColumn("service_record_corrections", "requester_name");
        repairNameColumn("service_record_corrections", "reviewed_by_name");
        repairNameColumn("file_assets", "uploader_name");
        repairNameColumn("donation_orders", "donor_name");
        repairNameColumn("donations", "donor_name");
    }

    private void repairNameColumn(String tableName, String columnName) {
        if (!tableExists(tableName) || !columnExists(tableName, columnName)) {
            return;
        }
        int updated = jdbcTemplate.update(
                "UPDATE " + tableName +
                        " SET " + columnName + " = CASE " + columnName +
                        " WHEN ? THEN ? WHEN ? THEN ? ELSE " + columnName + " END" +
                        " WHERE " + columnName + " IN (?, ?)",
                LEGACY_MOJIBAKE_ADMIN_DISPLAY_NAME,
                DEFAULT_ADMIN_DISPLAY_NAME,
                LEGACY_MOJIBAKE_ORGANIZER_DISPLAY_NAME,
                DEFAULT_ORGANIZER_DISPLAY_NAME,
                LEGACY_MOJIBAKE_ADMIN_DISPLAY_NAME,
                LEGACY_MOJIBAKE_ORGANIZER_DISPLAY_NAME
        );
        updated += jdbcTemplate.update(
                "UPDATE " + tableName +
                        " SET " + columnName + " = CASE " + columnName +
                        " WHEN ? THEN ? WHEN ? THEN ? ELSE " + columnName + " END" +
                        " WHERE " + columnName + " IN (?, ?)",
                LEGACY_GBK_MOJIBAKE_ADMIN_DISPLAY_NAME,
                DEFAULT_ADMIN_DISPLAY_NAME,
                LEGACY_GBK_MOJIBAKE_ORGANIZER_DISPLAY_NAME,
                DEFAULT_ORGANIZER_DISPLAY_NAME,
                LEGACY_GBK_MOJIBAKE_ADMIN_DISPLAY_NAME,
                LEGACY_GBK_MOJIBAKE_ORGANIZER_DISPLAY_NAME
        );
        if (updated > 0) {
            log.info("Historical name snapshot repaired: table={}, column={}, rows={}", tableName, columnName, updated);
        }
    }

    private boolean needsDisplayNameRepair(String displayName, Role role) {
        if (displayName == null || displayName.isBlank()) {
            return true;
        }
        if (role == Role.ADMIN && LEGACY_MOJIBAKE_ADMIN_DISPLAY_NAME.equals(displayName)) {
            return true;
        }
        if (role == Role.ORGANIZER && LEGACY_MOJIBAKE_ORGANIZER_DISPLAY_NAME.equals(displayName)) {
            return true;
        }
        if (role == Role.ADMIN && LEGACY_GBK_MOJIBAKE_ADMIN_DISPLAY_NAME.equals(displayName)) {
            return true;
        }
        if (role == Role.ORGANIZER && LEGACY_GBK_MOJIBAKE_ORGANIZER_DISPLAY_NAME.equals(displayName)) {
            return true;
        }
        return false;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private static String mojibake(String value) {
        return new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
    }

    private static String gbkMojibake(String value) {
        return new String(value.getBytes(StandardCharsets.UTF_8), java.nio.charset.Charset.forName("GBK"));
    }

    private static String safeDisplayName(String configuredValue, String fallbackValue) {
        if (configuredValue == null || configuredValue.isBlank()) {
            return fallbackValue;
        }
        if (configuredValue.equals(LEGACY_MOJIBAKE_ADMIN_DISPLAY_NAME) || configuredValue.equals(LEGACY_MOJIBAKE_ORGANIZER_DISPLAY_NAME)) {
            return fallbackValue;
        }
        if (configuredValue.equals(LEGACY_GBK_MOJIBAKE_ADMIN_DISPLAY_NAME) || configuredValue.equals(LEGACY_GBK_MOJIBAKE_ORGANIZER_DISPLAY_NAME)) {
            return fallbackValue;
        }
        return configuredValue;
    }

    @ConfigurationProperties(prefix = "vms.bootstrap")
    public record BootstrapProperties(
            boolean enabled,
            String adminUsername,
            String adminPassword,
            String adminDisplayName,
            String organizerUsername,
            String organizerPassword,
            String organizerDisplayName
    ) {
    }
}
