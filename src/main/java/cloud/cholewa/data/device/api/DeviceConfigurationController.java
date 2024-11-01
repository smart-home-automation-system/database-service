package cloud.cholewa.data.device.api;

import cloud.cholewa.data.device.service.DeviceConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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


    @GetMapping
    Mono<ResponseEntity<Void>> getDeviceConfiguration(
        @RequestBody(required = false) String dummyValueDueLoggingIssue
    ) {
        log.info("get device configuration");
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build());
    }
}
