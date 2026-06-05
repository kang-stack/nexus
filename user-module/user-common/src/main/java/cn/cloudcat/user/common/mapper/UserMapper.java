package cn.cloudcat.user.common.mapper;

import cn.cloudcat.user.common.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * acc_user_account 表 Mapper。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
