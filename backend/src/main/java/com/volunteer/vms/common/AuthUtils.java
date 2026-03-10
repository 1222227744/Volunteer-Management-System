package com.volunteer.vms.common;

import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

public final class AuthUtils {
    public static final String CURRENT_USER_ATTR = "CURRENT_USER";

    private AuthUtils() {
    }

    public static User currentUser(HttpServletRequest request) {
        Object user = request.getAttribute(CURRENT_USER_ATTR);
        if (!(user instanceof User currentUser)) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未登录或登录态失效");
        }
        return currentUser;
    }

    public static void requireRole(User user, Role... roles) {
        boolean hasRole = Arrays.stream(roles).anyMatch(role -> role == user.getRole());
        if (!hasRole) {
            throw new BizException(HttpStatus.FORBIDDEN, "权限不足");
        }
    }
}
