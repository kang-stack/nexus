package cn.cloudcat.user.common.service.impl;

import cn.cloudcat.user.common.entity.User;
import cn.cloudcat.user.common.mapper.UserMapper;
import cn.cloudcat.user.common.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public User getByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    @Override
    public User getByUserId(String userId) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getUserId, userId));
    }
}
