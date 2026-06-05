package cn.cloudcat.user.common.service.impl;

import cn.cloudcat.user.common.entity.SysAdmin;
import cn.cloudcat.user.common.mapper.AdminMapper;
import cn.cloudcat.user.common.service.AdminService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 管理员服务实现。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
public class AdminServiceImpl extends ServiceImpl<AdminMapper, SysAdmin> implements AdminService {

    @Override
    public SysAdmin getByUsername(String username) {
        return getOne(new LambdaQueryWrapper<SysAdmin>().eq(SysAdmin::getUsername, username));
    }

    @Override
    public SysAdmin getByAdminId(String adminId) {
        return getOne(new LambdaQueryWrapper<SysAdmin>().eq(SysAdmin::getAdminId, adminId));
    }
}
