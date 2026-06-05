package cn.cloudcat.customer.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({
        "cn.cloudcat.user.common.mapper",
        "cn.cloudcat.user.session.mapper",
        "cn.cloudcat.user.diagnosis.mapper",
        "cn.cloudcat.user.knowledge.mapper",
        "cn.cloudcat.user.message.review.mapper",
        "cn.cloudcat.user.portal.mapper"
})
public class MybatisMapperScanConfiguration {
}