package cn.cloudcat.customer.config;

import cn.cloudcat.user.common.entity.User;
import cn.cloudcat.common.core.id.SnowflakeIdGenerator;
import cn.cloudcat.user.common.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class W1DevAccountInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, "cat")) > 0) {
            return;
        }
        User user = new User();
        user.setUserId(SnowflakeIdGenerator.getInstance().nextIdStr());
        user.setUsername("cat");
        user.setPassword(passwordEncoder.encode("cat123"));
        user.setNickname("演示用户");
        user.setStatus("active");
        userMapper.insert(user);
    }
}
