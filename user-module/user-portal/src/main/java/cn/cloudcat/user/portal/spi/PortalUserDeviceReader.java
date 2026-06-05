package cn.cloudcat.user.portal.spi;

import cn.cloudcat.user.portal.entity.PortalDevice;
import cn.cloudcat.user.portal.service.DeviceService;
import cn.cloudcat.user.common.model.WifiDeviceSnapshot;
import cn.cloudcat.user.common.spi.UserDeviceReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PortalUserDeviceReader implements UserDeviceReader {

    private final DeviceService deviceService;

    @Override
    public List<WifiDeviceSnapshot> listByUserId(String userId) {
        List<PortalDevice> devices = deviceService.listByUserId(userId);
        if (devices == null || devices.isEmpty()) {
            return Collections.emptyList();
        }
        return devices.stream().map(PortalUserDeviceReader::toSnapshot).toList();
    }

    static WifiDeviceSnapshot toSnapshot(PortalDevice d) {
        return WifiDeviceSnapshot.builder()
                .deviceId(d.getDeviceId())
                .userId(d.getUserId())
                .deviceModel(d.getDeviceModel())
                .sn(d.getSn())
                .activationTime(d.getActivationTime())
                .dataUsedGb(d.getDataUsedGb())
                .dataTotalGb(d.getDataTotalGb())
                .expireTime(d.getExpireTime())
                .status(d.getStatus())
                .diagActivationStatus(d.getDiagActivationStatus())
                .diagRealnameStatus(d.getDiagRealnameStatus())
                .diagOnlineStatus(d.getDiagOnlineStatus())
                .diagSignalValue(d.getDiagSignalValue())
                .diagDeviceStatus(d.getDiagDeviceStatus())
                .build();
    }
}
