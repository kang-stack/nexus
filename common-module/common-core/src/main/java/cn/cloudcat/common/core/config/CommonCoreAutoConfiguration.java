package cn.cloudcat.common.core.config;

import cn.cloudcat.common.core.spring.SpringContextHolder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(SpringContextHolder.class)
public class CommonCoreAutoConfiguration {
}