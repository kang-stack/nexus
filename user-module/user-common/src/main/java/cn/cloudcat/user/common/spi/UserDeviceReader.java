package cn.cloudcat.user.common.spi;

import cn.cloudcat.user.common.model.WifiDeviceSnapshot;

import java.util.List;

/**
 * 查询用户绑定设备（实现由 icc-admin 提供）。
 */
public interface UserDeviceReader {

    List<WifiDeviceSnapshot> listByUserId(String userId);
}
