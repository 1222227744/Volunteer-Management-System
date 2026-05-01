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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@EnableConfigurationProperties(StartupDataInitializer.BootstrapProperties.class)
public class StartupDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupDataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties bootstrapProperties;

    public StartupDataInitializer(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  BootstrapProperties bootstrapProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapProperties = bootstrapProperties;
    }

    @Override
    public void run(String... args) {
        if (!bootstrapProperties.enabled()) {
            log.info("跳过默认账号初始化，vms.bootstrap.enabled=false");
            return;
        }
        ensureUser(
                bootstrapProperties.adminUsername(),
                bootstrapProperties.adminPassword(),
                bootstrapProperties.adminDisplayName(),
                Role.ADMIN
        );
        ensureUser(
                bootstrapProperties.organizerUsername(),
                bootstrapProperties.organizerPassword(),
                bootstrapProperties.organizerDisplayName(),
                Role.ORGANIZER
        );
    }

    private void ensureUser(String username, String rawPassword, String displayName, Role role) {
        User existing = userRepository.findByUsername(username).orElse(null);
        if (existing != null) {
            if (needsDisplayNameRepair(existing.getDisplayName(), role)) {
                existing.setDisplayName(displayName);
                userRepository.save(existing);
                log.info("已修正默认账号昵称: username={}, role={}", username, role);
            }
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setDisplayName(displayName);
        user.setRole(role);
        userRepository.save(user);
        log.info("初始化账号成功: username={}, role={}", username, role);
    }

    private boolean needsDisplayNameRepair(String displayName, Role role) {
        if (displayName == null || displayName.isBlank()) {
            return true;
        }
        if (role == Role.ADMIN && "ç³»ç»ç®¡çå".equals(displayName)) {
            return true;
        }
        if (role == Role.ORGANIZER && "ç»ç»æ¹è´¦å·".equals(displayName)) {
            return true;
        }
        return false;
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
