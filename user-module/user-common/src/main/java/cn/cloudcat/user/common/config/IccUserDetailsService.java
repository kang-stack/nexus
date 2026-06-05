package cn.cloudcat.user.common.config;

import cn.cloudcat.user.common.entity.SysAdmin;
import cn.cloudcat.user.common.entity.User;
import cn.cloudcat.user.common.service.AdminService;
import cn.cloudcat.user.common.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring Security 用户详情加载服务，支持管理员和普通用户两种身份。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IccUserDetailsService implements UserDetailsService {

    private final UserService userService;
    private final AdminService adminService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysAdmin admin = adminService.getByUsername(username);
        if (admin != null) {
            String role = admin.getRole() != null ? admin.getRole() : "ADMIN";
            return new org.springframework.security.core.userdetails.User(
                    admin.getAdminId(),
                    admin.getPassword(),
                    "active".equals(admin.getStatus()),
                    true, true, true,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
        }

        User user = userService.getByUsername(username);
        if (user != null) {
            return new org.springframework.security.core.userdetails.User(
                    user.getUserId(),
                    user.getPassword(),
                    "active".equals(user.getStatus()),
                    true, true, true,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );
        }

        throw new UsernameNotFoundException("用户不存在: " + username);
    }
}
