package com.volunteer.vms.auth;

import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final TokenSessionService tokenSessionService;

    public AuthInterceptor(TokenSessionService tokenSessionService) {
        this.tokenSessionService = tokenSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "无效的登录凭证");
        }
        Optional<User> userOptional = tokenSessionService.resolveUser(token);
        User user = userOptional.orElseThrow(() -> new BizException(HttpStatus.UNAUTHORIZED, "登录态已失效，请重新登录"));
        request.setAttribute(AuthUtils.CURRENT_USER_ATTR, user);
        return true;
    }
}
