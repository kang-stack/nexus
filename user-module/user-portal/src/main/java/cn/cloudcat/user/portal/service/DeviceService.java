package cn.cloudcat.user.portal.service;

import cn.cloudcat.user.portal.entity.PortalDevice;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * 设备 Service 接口。
 *
 * @author Kang
 * @since 2026-06-02
 */
public interface DeviceService extends IService<PortalDevice> {

    /** 根据设备ID查询设备 */
    PortalDevice getByDeviceId(String deviceId);

    /** 查询某用户绑定的所有设备 */
    List<PortalDevice> listByUserId(String userId);

    /** 为尚未绑定设备的 C 端用户各创建一台默认调试设备 */
    int seedDevicesForAllUsers();

    /** 下拉选项：userId + username */
    List<Map<String, String>> listUserOptions();
}
