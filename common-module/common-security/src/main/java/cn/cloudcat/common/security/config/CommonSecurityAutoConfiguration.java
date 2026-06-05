package cn.cloudcat.common.security.config;

import cn.cloudcat.common.security.jwt.JwtAuthenticationFilter;
import cn.cloudcat.common.security.jwt.JwtTokenProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({JwtTokenProvider.class, JwtAuthenticationFilter.class})
public class CommonSecurityAutoConfiguration {
}