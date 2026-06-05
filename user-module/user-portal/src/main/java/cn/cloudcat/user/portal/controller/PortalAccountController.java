package cn.cloudcat.user.portal.controller;

import cn.cloudcat.user.common.constant.AdminRole;
import cn.cloudcat.user.common.entity.SysAdmin;
import cn.cloudcat.common.core.id.SnowflakeIdGenerator;
import cn.cloudcat.user.common.service.AdminService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 后台账号管理 — SUPER_ADMIN 可管理 ADMIN/OPERATOR，ADMIN 可管理 OPERATOR
 */
@RestController
@RequestMapping("/api/cs/v1/admin/account")
@RequiredArgsConstructor
@Slf4j
public class PortalAccountController {

    private final AdminService adminService;
    private final PasswordEncoder passwordEncoder;

    /** 列表 — SUPER_ADMIN 看全部，ADMIN 看自己和 OPERATOR */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public List<AccountVO> list() {
        String currentRole = getCurrentRole();
        SysAdmin self = adminService.getByAdminId(getCurrentUserId());

        return adminService.list().stream()
                .filter(a -> {
                    if ("SUPER_ADMIN".equals(currentRole)) return true;
                    // ADMIN 只能看到自己和 OPERATOR
                    return a.getAdminId().equals(self.getAdminId())
                            || "OPERATOR".equals(a.getRole());
                })
                .map(AccountVO::from)
                .collect(Collectors.toList());
    }

    /** 创建 — SUPER_ADMIN 可创建 ADMIN/OPERATOR，ADMIN 只能创建 OPERATOR */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> create(@RequestBody CreateRequest req) {
        String currentRole = getCurrentRole();

        if (adminService.getByUsername(req.getUsername()) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        }
        if (!isValidRole(req.getRole())) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的角色: " + req.getRole()));
        }
        if ("SUPER_ADMIN".equals(req.getRole())) {
            return ResponseEntity.badRequest().body(Map.of("error", "不允许创建超级管理员"));
        }
        // ADMIN 只能创建 OPERATOR
        if ("ADMIN".equals(currentRole) && !"OPERATOR".equals(req.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "仅允许创建运营人员账号"));
        }

        SysAdmin admin = new SysAdmin();
        admin.setAdminId(SnowflakeIdGenerator.getInstance().nextIdStr());
        admin.setUsername(req.getUsername());
        admin.setPassword(passwordEncoder.encode(req.getPassword()));
        admin.setRealName(req.getRealName() != null ? req.getRealName() : req.getUsername());
        admin.setRole(req.getRole());
        admin.setStatus("active");
        adminService.save(admin);

        log.info("{} 创建了后台账号: username={}, role={}", currentRole, req.getUsername(), req.getRole());
        return ResponseEntity.ok(AccountVO.from(admin));
    }

    /** 修改角色 — 仅 SUPER_ADMIN 可操作 */
    @PutMapping("/{adminId}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> updateRole(@PathVariable String adminId, @RequestBody Map<String, String> body) {
        SysAdmin target = adminService.getByAdminId(adminId);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        if ("SUPER_ADMIN".equals(target.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "不允许修改超级管理员"));
        }
        String newRole = body.get("role");
        if (!isValidRole(newRole) || "SUPER_ADMIN".equals(newRole)) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的角色: " + newRole));
        }
        target.setRole(newRole);
        adminService.updateById(target);
        log.info("管理员角色变更: username={}, newRole={}", target.getUsername(), newRole);
        return ResponseEntity.ok(Map.of("message", "角色已更新"));
    }

    /** 禁用/启用 — SUPER_ADMIN 全部，ADMIN 仅 OPERATOR */
    @PutMapping("/{adminId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> toggleStatus(@PathVariable String adminId, @RequestBody Map<String, String> body) {
        SysAdmin target = adminService.getByAdminId(adminId);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canManage(target)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权限操作该账号"));
        }

        String status = body.get("status");
        if (!"active".equals(status) && !"disabled".equals(status)) {
            return ResponseEntity.badRequest().body(Map.of("error", "状态只能是 active 或 disabled"));
        }
        target.setStatus(status);
        adminService.updateById(target);
        log.info("管理员状态变更: username={}, status={}", target.getUsername(), status);
        return ResponseEntity.ok(Map.of("message", "状态已更新"));
    }

    /** 重置密码 — SUPER_ADMIN 全部，ADMIN 仅 OPERATOR */
    @PutMapping("/{adminId}/password")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> resetPassword(@PathVariable String adminId, @RequestBody Map<String, String> body) {
        SysAdmin target = adminService.getByAdminId(adminId);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canManage(target)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权限操作该账号"));
        }

        String newPassword = body.get("password");
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码至少6位"));
        }
        target.setPassword(passwordEncoder.encode(newPassword));
        adminService.updateById(target);
        log.info("管理员密码重置: username={}", target.getUsername());
        return ResponseEntity.ok(Map.of("message", "密码已重置"));
    }

    /** 删除 — SUPER_ADMIN 删 ADMIN/OPERATOR，ADMIN 删 OPERATOR */
    @DeleteMapping("/{adminId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> delete(@PathVariable String adminId) {
        SysAdmin target = adminService.getByAdminId(adminId);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        if ("SUPER_ADMIN".equals(target.getRole())) {
            return ResponseEntity.status(403).body(Map.of("error", "不允许删除超级管理员"));
        }
        if (!canManage(target)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权限操作该账号"));
        }

        adminService.removeById(target.getId());
        log.info("管理员账号已删除: username={}, role={}", target.getUsername(), target.getRole());
        return ResponseEntity.ok(Map.of("message", "账号已删除"));
    }

    // ===== helpers =====

    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().stream()
                    .map(g -> g.getAuthority().replace("ROLE_", ""))
                    .findFirst().orElse("");
        }
        return "";
    }

    /** 当前用户是否有权管理目标账号 */
    private boolean canManage(SysAdmin target) {
        String role = getCurrentRole();
        if ("SUPER_ADMIN".equals(role)) return true;
        if ("ADMIN".equals(role) && "OPERATOR".equals(target.getRole())) return true;
        return false;
    }

    private boolean isValidRole(String role) {
        try {
            AdminRole.valueOf(role);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Data
    public static class AccountVO {
        private String adminId;
        private String username;
        private String realName;
        private String role;
        private String status;
        private String createdAt;

        public static AccountVO from(SysAdmin admin) {
            AccountVO vo = new AccountVO();
            vo.setAdminId(admin.getAdminId());
            vo.setUsername(admin.getUsername());
            vo.setRealName(admin.getRealName());
            vo.setRole(admin.getRole());
            vo.setStatus(admin.getStatus());
            vo.setCreatedAt(admin.getCreatedAt() != null ? admin.getCreatedAt().toString() : null);
            return vo;
        }
    }

    @Data
    public static class CreateRequest {
        private String username;
        private String password;
        private String realName;
        private String role;
    }
}
