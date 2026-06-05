package cn.cloudcat.user.message.auth.controller;

import cn.cloudcat.user.common.entity.User;
import cn.cloudcat.common.security.jwt.JwtTokenProvider;
import cn.cloudcat.common.core.id.SnowflakeIdGenerator;
import cn.cloudcat.user.common.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * C 端用户认证 — 仅操作 acc_user_account 表。
 */
@RestController
@RequestMapping("/api/cs/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class MessageAuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        User user = userService.getByUsername(request.getUsername());
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "用户名或密码错误"));
        }
        if (!"active".equals(user.getStatus())) {
            return ResponseEntity.status(403).body(Map.of("error", "账号已被禁用"));
        }
        String token = jwtTokenProvider.generateToken(user.getUserId(), "USER");
        return ResponseEntity.ok(new LoginResponse(token, user.getUserId(), user.getUsername(), "USER"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        if (userService.getByUsername(request.getUsername()) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        }

        User user = new User();
        user.setUserId(SnowflakeIdGenerator.getInstance().nextIdStr());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setPhone(request.getPhone());
        user.setStatus("active");
        userService.save(user);

        String token = jwtTokenProvider.generateToken(user.getUserId(), "USER");
        log.info("新用户注册: username={}, userId={}", request.getUsername(), user.getUserId());
        return ResponseEntity.ok(new LoginResponse(token, user.getUserId(), user.getUsername(), "USER"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String bearerToken) {
        String token = bearerToken.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "Token已过期或无效"));
        }
        String userId = jwtTokenProvider.getUserId(token);
        String role = jwtTokenProvider.getRole(token);
        String newToken = jwtTokenProvider.generateToken(userId, role);
        return ResponseEntity.ok(Map.of("token", newToken));
    }

    @GetMapping("/me")
    public ResponseEntity<?> currentUser(@RequestHeader("Authorization") String bearerToken) {
        String token = bearerToken.substring(7);
        String userId = jwtTokenProvider.getUserId(token);
        User user = userService.getByUserId(userId);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        }
        return ResponseEntity.ok(Map.of(
                "userId", user.getUserId(),
                "username", user.getUsername(),
                "role", "USER"
        ));
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 20, message = "用户名长度需在3-20位之间")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 20, message = "密码长度需在6-20位之间")
        private String password;

        @Size(max = 20, message = "昵称长度不能超过20位")
        private String nickname;

        @Pattern(regexp = "^(1[3-9]\\d{9})?$", message = "手机号格式不正确")
        private String phone;
    }

    @Data
    @RequiredArgsConstructor
    public static class LoginResponse {
        private final String token;
        private final String userId;
        private final String username;
        private final String role;
    }
}
