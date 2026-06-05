package cn.cloudcat.user.common.service;

import cn.cloudcat.user.common.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 用户服务接口。
 *
 * @author Kang
 * @since 2026-06-02
 */
public interface UserService extends IService<User> {

    /**
     * 根据用户名查询用户。
     */
    User getByUsername(String username);

    /**
     * 根据用户 ID 查询用户。
     */
    User getByUserId(String userId);
}
