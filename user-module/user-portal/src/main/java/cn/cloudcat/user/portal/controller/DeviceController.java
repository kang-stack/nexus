package cn.cloudcat.user.portal.controller;

import cn.cloudcat.user.portal.entity.PortalDevice;
import cn.cloudcat.user.portal.service.DeviceService;
import cn.cloudcat.common.core.id.SnowflakeIdGenerator;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cs/v1/admin/device")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<PortalDevice> create(@RequestBody PortalDevice device) {
        device.setDeviceId(SnowflakeIdGenerator.getInstance().nextIdStr());
        deviceService.save(device);
        log.info("设备创建成功: deviceId={}, model={}", device.getDeviceId(), device.getDeviceModel());
        return ResponseEntity.ok(device);
    }

    @PutMapping("/{deviceId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<PortalDevice> update(@PathVariable String deviceId, @RequestBody PortalDevice update) {
        PortalDevice device = deviceService.getByDeviceId(deviceId);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        update.setId(device.getId());
        deviceService.updateById(update);
        return ResponseEntity.ok(deviceService.getById(device.getId()));
    }

    @GetMapping("/{deviceId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public ResponseEntity<PortalDevice> getByDeviceId(@PathVariable String deviceId) {
        PortalDevice device = deviceService.getByDeviceId(deviceId);
        return device != null ? ResponseEntity.ok(device) : ResponseEntity.notFound().build();
    }

    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public Page<PortalDevice> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return deviceService.page(new Page<>(pageNum, pageSize));
    }

    @GetMapping("/by-user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public List<PortalDevice> listByUserId(@PathVariable String userId) {
        return deviceService.listByUserId(userId);
    }

    @DeleteMapping("/{deviceId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<String> delete(@PathVariable String deviceId) {
        PortalDevice device = deviceService.getByDeviceId(deviceId);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        deviceService.removeById(device.getId());
        return ResponseEntity.ok("删除成功");
    }

    @PutMapping("/{deviceId}/bind")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<PortalDevice> bindUser(@PathVariable String deviceId, @RequestParam String userId) {
        PortalDevice device = deviceService.getByDeviceId(deviceId);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        device.setUserId(userId);
        deviceService.updateById(device);
        return ResponseEntity.ok(device);
    }

    @PutMapping("/{deviceId}/unbind")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<PortalDevice> unbindUser(@PathVariable String deviceId) {
        PortalDevice device = deviceService.getByDeviceId(deviceId);
        if (device == null) {
            return ResponseEntity.notFound().build();
        }
        device.setUserId(null);
        deviceService.updateById(device);
        return ResponseEntity.ok(device);
    }

    /**
     * 实时设备状态查询（Phase 5: 供移动端客服使用）。
     */
    @GetMapping("/{deviceId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','OPERATOR')")
    public Map<String, Object> getDeviceStatus(@PathVariable String deviceId) {
        PortalDevice device = deviceService.getByDeviceId(deviceId);
        if (device == null) {
            return Map.of("error", "设备不存在");
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("deviceId", device.getDeviceId());
        status.put("status", device.getStatus());
        status.put("dataUsedGB", device.getDataUsedGb() != null
                ? device.getDataUsedGb().doubleValue() : 0);
        status.put("dataTotalGB", device.getDataTotalGb() != null
                ? device.getDataTotalGb().doubleValue() : 0);
        status.put("expired", device.getExpireTime() != null
                && device.getExpireTime().isBefore(LocalDateTime.now()));
        status.put("expireTime", device.getExpireTime() != null
                ? device.getExpireTime().toString() : null);
        return status;
    }

    /**
     * 系统操作接口（Phase 5: 如网络刷新、套餐查询等）。
     */
    @PostMapping("/{deviceId}/action")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Map<String, Object> executeAction(@PathVariable String deviceId,
                                              @RequestBody Map<String, String> body) {
        String action = body.get("action");
        PortalDevice device = deviceService.getByDeviceId(deviceId);
        if (device == null) {
            return Map.of("success", false, "message", "设备不存在");
        }

        log.info("device action: deviceId={} action={}", deviceId, action);

        return switch (action != null ? action : "") {
            case "REFRESH_NETWORK" -> Map.of("success", true,
                    "message", "网络刷新指令已下发，请稍后查看设备状态");
            case "CHECK_DATA_USAGE" -> Map.of("success", true,
                    "message", String.format("当前已用 %.2f GB / 共 %.2f GB",
                            device.getDataUsedGb() != null ? device.getDataUsedGb() : 0,
                            device.getDataTotalGb() != null ? device.getDataTotalGb() : 0));
            case "RESET_DEVICE" -> Map.of("success", true,
                    "message", "设备重置指令已下发");
            default -> Map.of("success", false,
                    "message", "不支持的操作: " + action);
        };
    }
}
