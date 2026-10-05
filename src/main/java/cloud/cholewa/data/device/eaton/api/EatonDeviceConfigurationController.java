package cloud.cholewa.data.device.eaton.api;

import cloud.cholewa.data.device.eaton.model.EatonDataPoint;
import cloud.cholewa.data.device.eaton.service.EatonDeviceConfigurationService;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import cloud.cholewa.home.model.EatonDeviceConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
@RequestMapping("device/configuration/eaton")
public class EatonDeviceConfigurationController {

    private final EatonDeviceConfigurationService eatonDeviceConfigurationService;

    @PostMapping
    Mono<ResponseEntity<Void>> addEatonDeviceConfiguration(
        @Valid @RequestBody final EatonDeviceConfiguration eatonDeviceConfiguration
    ) {
        return eatonDeviceConfigurationService.add(eatonDeviceConfiguration)
            .doOnSubscribe(subscription ->
                log.info(
                    "Adding Eaton device configuration for point: {}, on gateway: {}",
                    eatonDeviceConfiguration.getPoint(),
                    eatonDeviceConfiguration.getGateway()
                ))
            .then(Mono.just(new ResponseEntity<>(HttpStatus.CREATED)));
    }

    //A point outside the range is answered with 400 here, instead of running a query that cannot
    //match and presenting a bad request as a missing configuration (404). WebFlux validates the
    //constraints of a query parameter by itself - no @Validated on the class, which would only
    //swap that for an AOP proxy and validate the body of the POST a second time.
    @GetMapping()
    Mono<ResponseEntity<EatonConfigurationResponse>> getEatonDeviceConfiguration(
        @RequestParam
        @Min(value = EatonDataPoint.MIN, message = EatonDataPoint.OUT_OF_RANGE)
        @Max(value = EatonDataPoint.MAX, message = EatonDataPoint.OUT_OF_RANGE)
        final int point,
        @RequestParam final String gateway
    ) {
        return eatonDeviceConfigurationService.get(point, gateway)
            .doOnSubscribe(subscription ->
                log.info("Querying for Eaton device configuration for point: {}, on gateway: {}", point, gateway))
            .map(ResponseEntity::ok);
    }
}
