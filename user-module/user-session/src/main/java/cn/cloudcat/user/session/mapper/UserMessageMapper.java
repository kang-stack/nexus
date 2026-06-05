package cn.cloudcat.user.session.mapper;

import cn.cloudcat.user.session.entity.UserMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * acc_user_message 表 Mapper。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {
}
