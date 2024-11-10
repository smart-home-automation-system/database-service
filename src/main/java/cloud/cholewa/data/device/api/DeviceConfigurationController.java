package cloud.cholewa.data.device.api;

import cloud.cholewa.data.device.service.DeviceConfigurationService;
import cloud.cholewa.home.model.DeviceConfigurationRequest;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("device/configuration")
public class DeviceConfigurationController {

    private final DeviceConfigurationService deviceConfigurationService;

    @PostMapping
    Mono<ResponseEntity<Void>> addDeviceConfiguration(
        @Valid @RequestBody DeviceConfigurationRequest deviceConfigurationRequest
    ) {
        log.info(
            "Adding device configuration device: {}, to room {}",
            deviceConfigurationRequest.getDeviceType(),
            deviceConfigurationRequest.getRoomName()
        );
        return deviceConfigurationService.addDeviceConfiguration(deviceConfigurationRequest)
            .then(Mono.empty());
    }

    @GetMapping("/eaton")
    Mono<ResponseEntity<EatonConfigurationResponse>> getDeviceConfigurationByDataPointAndEatonGateway(
        @RequestParam final int dataPoint,
        @RequestParam final String eatonGateway
    ) {
        log.info("Querying for Eaton device configuration for DP: {}, on gateway: {}", dataPoint, eatonGateway);

        return deviceConfigurationService.getDeviceConfigurationByDataPointAndGateway(dataPoint, eatonGateway)
            .map(ResponseEntity::ok);
    }
}
