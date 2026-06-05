package cn.cloudcat.user.portal.service.impl;

import cn.cloudcat.user.portal.entity.PortalDevice;
import cn.cloudcat.user.portal.mapper.DeviceMapper;
import cn.cloudcat.user.portal.service.DeviceService;
import cn.cloudcat.user.common.entity.User;
import cn.cloudcat.common.core.id.SnowflakeIdGenerator;
import cn.cloudcat.user.common.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备 Service 实现类。
 *
 * @author Kang
 * @since 2026-06-02
 */
@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, PortalDevice> implements DeviceService {

    private final UserMapper userMapper;

    public DeviceServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public PortalDevice getByDeviceId(String deviceId) {
        return getOne(new LambdaQueryWrapper<PortalDevice>().eq(PortalDevice::getDeviceId, deviceId));
    }

    @Override
    public List<PortalDevice> listByUserId(String userId) {
        return list(new LambdaQueryWrapper<PortalDevice>().eq(PortalDevice::getUserId, userId));
    }

    @Override
    @Transactional
    public int seedDevicesForAllUsers() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getDeleted, 0)
                .eq(User::getStatus, "active"));
        int created = 0;
        SnowflakeIdGenerator idGen = SnowflakeIdGenerator.getInstance();
        LocalDateTime now = LocalDateTime.now();
        for (User user : users) {
            if (user.getUserId() == null || user.getUserId().isBlank()) {
                continue;
            }
            long bound = count(new LambdaQueryWrapper<PortalDevice>()
                    .eq(PortalDevice::getUserId, user.getUserId()));
            if (bound > 0) {
                continue;
            }
            PortalDevice device = buildDefaultDebugDevice(user, idGen.nextIdStr(), now);
            save(device);
            created++;
        }
        return created;
    }

    @Override
    public List<Map<String, String>> listUserOptions() {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getDeleted, 0)
                .orderByAsc(User::getUsername));
        List<Map<String, String>> options = new ArrayList<>();
        for (User user : users) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("userId", user.getUserId());
            row.put("username", user.getUsername());
            row.put("label", user.getUsername() + " (" + user.getUserId() + ")");
            options.add(row);
        }
        return options;
    }

    private static PortalDevice buildDefaultDebugDevice(User user, String deviceId, LocalDateTime now) {
        PortalDevice device = new PortalDevice();
        device.setDeviceId(deviceId);
        device.setUserId(user.getUserId());
        device.setDeviceModel("云客猫 MIFI 调试机");
        String suffix = user.getUserId().length() > 8
                ? user.getUserId().substring(user.getUserId().length() - 8)
                : user.getUserId();
        device.setSn("SN" + suffix);
        device.setImei("86" + suffix);
        device.setActivationTime(now.minusDays(7));
        device.setDataPlan("月享30GB");
        device.setDataUsedGb(new BigDecimal("5.00"));
        device.setDataTotalGb(new BigDecimal("30.00"));
        device.setExpireTime(now.plusDays(365));
        device.setStatus("active");
        device.setDiagActivationStatus("已激活");
        device.setDiagRealnameStatus("全部实名");
        device.setDiagOnlineStatus("在线");
        device.setDiagSignalValue("信号好");
        device.setRemark("系统自动生成的调试设备");
        return device;
    }
}
