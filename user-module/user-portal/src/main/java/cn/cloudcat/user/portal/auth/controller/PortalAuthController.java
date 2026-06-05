package cn.cloudcat.user.portal.auth.controller;

import cn.cloudcat.user.common.entity.SysAdmin;
import cn.cloudcat.common.security.jwt.JwtTokenProvider;
import cn.cloudcat.user.common.service.AdminService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 鍚庡彴绠＄悊鍛樿璇?鈥?浠呮搷浣?acc_user_admin 琛紝涓嶆敮鎸佹敞鍐? */
@RestController
@RequestMapping("/api/cs/v1/admin/auth")
@RequiredArgsConstructor
@Slf4j
public class PortalAuthController {

    private final AdminService adminService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        SysAdmin admin = adminService.getByUsername(request.getUsername());
        if (admin == null) {
            return ResponseEntity.status(401).body(Map.of("error", "账号不存在"));
        }
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "密码错误"));
        }
        if (!"active".equals(admin.getStatus())) {
            return ResponseEntity.status(403).body(Map.of("error", "账号已被禁用"));
        }
        String token = jwtTokenProvider.generateToken(admin.getAdminId(), admin.getRole());
        log.info("绠＄悊鍛樼櫥褰? username={}, role={}", admin.getUsername(), admin.getRole());
        return ResponseEntity.ok(new LoginResponse(token, admin.getAdminId(), admin.getUsername(), admin.getRole()));
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
        SysAdmin admin = adminService.getByAdminId(userId);
        if (admin == null) {
            return ResponseEntity.status(404).body(Map.of("error", "管理员不存在"));
        }
        return ResponseEntity.ok(Map.of(
                "userId", admin.getAdminId(),
                "username", admin.getUsername(),
                "role", admin.getRole(),
                "realName", admin.getRealName() != null ? admin.getRealName() : ""
        ));
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
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
