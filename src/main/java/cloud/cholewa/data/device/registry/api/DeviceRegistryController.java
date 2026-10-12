package cloud.cholewa.data.device.registry.api;

import cloud.cholewa.data.device.registry.service.DeviceRegistryService;
import cloud.cholewa.home.model.SmartDevice;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    //the answer is the device as the registry stored it, with the id it got
    @PostMapping
    Mono<ResponseEntity<SmartDevice>> addDevice(@Valid @RequestBody final SmartDevice device) {
        return deviceRegistryService.add(device)
            .map(added -> ResponseEntity.status(HttpStatus.CREATED).body(added));
    }

    //PUT, not PATCH: the device is replaced by the payload, whole - what the payload leaves out
    //is gone, and enabled is taken from it like everything else. The model has five required
    //fields, so a part of a device would not get past @Valid anyway
    @PutMapping("{id}")
    Mono<ResponseEntity<SmartDevice>> replaceDevice(
        @PathVariable final long id,
        @Valid @RequestBody final SmartDevice device
    ) {
        return deviceRegistryService.replace(id, device)
            .map(ResponseEntity::ok);
    }

    //the switch of a list: one device on or off without sending the rest of it
    @PostMapping("{id}/enable")
    Mono<ResponseEntity<SmartDevice>> enableDevice(@PathVariable final long id) {
        return deviceRegistryService.enable(id)
            .map(ResponseEntity::ok);
    }

    @PostMapping("{id}/disable")
    Mono<ResponseEntity<SmartDevice>> disableDevice(@PathVariable final long id) {
        return deviceRegistryService.disable(id)
            .map(ResponseEntity::ok);
    }

    @DeleteMapping("{id}")
    Mono<ResponseEntity<Void>> removeDevice(@PathVariable final long id) {
        return deviceRegistryService.remove(id)
            .then(Mono.just(ResponseEntity.noContent().build()));
    }
}
