package com.volunteer.vms.auth;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.user.AccountStatus;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 接口层：实现 FR-01 用户注册、登录、登出与当前登录用户查询。
 * 对应《系统设计说明书》中的接口层，由控制器接收请求并调用认证基础设施。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenSessionService tokenSessionService;
    private final AuditLogService auditLogService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          TokenSessionService tokenSessionService,
                          AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenSessionService = tokenSessionService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/register")
    public ApiResponse<UserProfileResponse> register(HttpServletRequest httpRequest,
                                                     @Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setRole(Role.VOLUNTEER);
        User saved = userRepository.save(user);
        auditLogService.log(
                httpRequest,
                saved,
                "USER_REGISTERED",
                "USER",
                saved.getId(),
                "用户完成注册"
        );
        return ApiResponse.success(UserProfileResponse.from(saved));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(HttpServletRequest httpRequest,
                                                  @Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BizException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getAccountStatus() != AccountStatus.ENABLED) {
            throw new BizException(HttpStatus.FORBIDDEN, "账号当前不可登录，请联系管理员");
        }
        String token = tokenSessionService.createToken(user);
        auditLogService.log(
                httpRequest,
                user,
                "USER_LOGGED_IN",
                "USER",
                user.getId(),
                "用户登录成功"
        );
        return ApiResponse.success(Map.of(
                "token", token,
                "user", UserProfileResponse.from(user)
        ));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        User currentUser = null;
        try {
            currentUser = AuthUtils.currentUser(request);
        } catch (BizException ignored) {
            // logout can still proceed when the token is already invalid
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenSessionService.removeToken(authHeader.substring(7).trim());
        }
        if (currentUser != null) {
            auditLogService.log(
                    request,
                    currentUser,
                    "USER_LOGGED_OUT",
                    "USER",
                    currentUser.getId(),
                    "用户退出登录"
            );
        }
        return ApiResponse.success();
    }

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        return ApiResponse.success(UserProfileResponse.from(currentUser));
    }

    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 30, message = "用户名长度应为3-30")
            String username,
            @NotBlank(message = "密码不能为空")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "密码至少8位且需同时包含字母和数字")
            String password,
            @NotBlank(message = "昵称不能为空")
            @Size(max = 50, message = "昵称最多50个字符")
            String displayName
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空")
            String username,
            @NotBlank(message = "密码不能为空")
            String password
    ) {
    }

    public record UserProfileResponse(Long id,
                                      String username,
                                      String displayName,
                                      Role role,
                                      Integer points,
                                      String phone,
                                      String serviceIntention,
                                      AccountStatus accountStatus,
                                      com.volunteer.vms.user.VerificationStatus verificationStatus,
                                      String verificationComment) {
        static UserProfileResponse from(User user) {
            return new UserProfileResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getRole(),
                    user.getPoints(),
                    user.getPhone(),
                    user.getServiceIntention(),
                    user.getAccountStatus(),
                    user.getVerificationStatus(),
                    user.getVerificationComment()
            );
        }
    }
}
