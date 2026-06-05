package cn.cloudcat.user.portal.mapper;

import cn.cloudcat.user.portal.entity.PortalDevice;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备表 Mapper 接口。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Mapper
public interface DeviceMapper extends BaseMapper<PortalDevice> {
}
