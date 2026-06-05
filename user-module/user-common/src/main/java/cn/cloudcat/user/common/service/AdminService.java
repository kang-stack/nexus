package cn.cloudcat.user.common.service;

import cn.cloudcat.user.common.entity.SysAdmin;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 管理员服务接口。
 *
 * @author Kang
 * @since 2026-06-02
 */
public interface AdminService extends IService<SysAdmin> {

    /**
     * 根据用户名查询管理员。
     */
    SysAdmin getByUsername(String username);

    /**
     * 根据管理员 ID 查询管理员。
     */
    SysAdmin getByAdminId(String adminId);
}
