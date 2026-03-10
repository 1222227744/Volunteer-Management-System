package com.volunteer.vms.config;

import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class StartupDataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupDataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public StartupDataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        ensureUser("admin", "admin123", "系统管理员", Role.ADMIN);
        ensureUser("organizer", "organizer123", "组织方账号", Role.ORGANIZER);
    }

    private void ensureUser(String username, String rawPassword, String displayName, Role role) {
        if (userRepository.existsByUsername(username)) {
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
}
