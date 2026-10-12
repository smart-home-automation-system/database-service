package cloud.cholewa.data.device.registry.api;

import cloud.cholewa.data.device.registry.service.DeviceRegistryService;
import cloud.cholewa.home.model.SmartDevice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("device/registry")
public class DeviceRegistryController {

    private final DeviceRegistryService deviceRegistryService;

    //kind and room are the values of the SDK enums (opening sensor, living room), read by the
    //service: Spring would convert an enum parameter by the name of its constant
    @GetMapping
    Mono<ResponseEntity<List<SmartDevice>>> getDevices(
        @RequestParam(required = false) final String kind,
        @RequestParam(required = false) final String room,
        @RequestParam(required = false) final Boolean enabled
    ) {
        return deviceRegistryService.find(kind, room, enabled)
            .doOnSubscribe(subscription ->
                log.info("Querying for devices of kind: {}, in room: {}, enabled: {}", kind, room, enabled))
            .map(ResponseEntity::ok);
    }

    @GetMapping("{id}")
    Mono<ResponseEntity<SmartDevice>> getDevice(@PathVariable final long id) {
        return deviceRegistryService.get(id)
            .doOnSubscribe(subscription -> log.info("Querying for device: {}", id))
            .map(ResponseEntity::ok);
    }
}
