package com.volunteer.vms.auth;

import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenSessionService tokenSessionService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          TokenSessionService tokenSessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenSessionService = tokenSessionService;
    }

    @PostMapping("/register")
    public ApiResponse<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setRole(Role.VOLUNTEER);
        User saved = userRepository.save(user);
        return ApiResponse.success(UserProfileResponse.from(saved));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BizException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = tokenSessionService.createToken(user);
        return ApiResponse.success(Map.of(
                "token", token,
                "user", UserProfileResponse.from(user)
        ));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenSessionService.removeToken(authHeader.substring(7).trim());
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
            @Size(min = 6, max = 64, message = "密码长度应为6-64")
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

    public record UserProfileResponse(Long id, String username, String displayName, Role role, Integer points) {
        static UserProfileResponse from(User user) {
            return new UserProfileResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getRole(),
                    user.getPoints()
            );
        }
    }
}
