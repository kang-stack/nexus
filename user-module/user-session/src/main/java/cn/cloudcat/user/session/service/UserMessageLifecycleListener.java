package cn.cloudcat.user.session.service;

import cn.cloudcat.user.session.entity.UserMessage;

/**
 * acc_user_message 持久化后回调（如 ES 索引）。
 *
 * @author Kang
 * @since 2026-06-02
 */
public interface UserMessageLifecycleListener {

    void onMessageSaved(UserMessage message);
}
