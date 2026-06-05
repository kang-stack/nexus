package cn.cloudcat.user.session.mapper;

import cn.cloudcat.user.session.entity.UserConversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * acc_user_conversation 表 Mapper。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Mapper
public interface UserConversationMapper extends BaseMapper<UserConversation> {
}
