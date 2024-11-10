package cloud.cholewa.data.device.service;

import cloud.cholewa.data.device.model.DeviceConfigurationEntity;
import cloud.cholewa.data.device.repository.DeviceConfigurationRepository;
import cloud.cholewa.data.error.InvalidDeviceConfigurationException;
import cloud.cholewa.home.model.DeviceConfiguration;
import cloud.cholewa.home.model.DeviceConfigurationRequest;
import cloud.cholewa.home.model.DeviceType;
import cloud.cholewa.home.model.EatonConfiguration;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import cloud.cholewa.home.model.EatonGateway;
import cloud.cholewa.home.model.IotVendor;
import cloud.cholewa.home.model.RoomName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceConfigurationServiceTest {

    @Mock
    private DeviceConfigurationRepository repository;

    @InjectMocks
    private DeviceConfigurationService sut;

    @Test
    void should_add_device_configuration() {
        DeviceConfigurationRequest request = DeviceConfigurationRequest.builder()
            .roomName(RoomName.BEDROOM.getValue())
            .deviceType(DeviceType.TEMPERATURE_SENSOR.getValue())
            .iotVendor(IotVendor.EATON.getValue())
            .deviceConfiguration(
                DeviceConfiguration.builder()
                    .eatonConfiguration(EatonConfiguration.builder()
                        .dataPoint(1)
                        .eatonGateway(EatonGateway.LIGHTS)
                        .build())
                    .build()
            )
            .build();

        when(repository.existsByIotVendorAndDataPointAndEatonGateway(any(), any(), any()))
            .thenReturn(Mono.just(false));

        when(repository.save(any())).thenReturn(Mono.just(DeviceConfigurationEntity.builder().build()));

        sut.addDeviceConfiguration(request)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(repository, times(1))
            .existsByIotVendorAndDataPointAndEatonGateway(any(), any(), any());

        verify(repository, times(1)).save(any());
    }

    @Test
    void should_not_add_device_configuration_when_device_exists() {
        DeviceConfigurationRequest request = DeviceConfigurationRequest.builder()
            .roomName("roomName")
            .deviceType(DeviceType.TEMPERATURE_SENSOR.getValue())
            .iotVendor(IotVendor.EATON.getValue())
            .deviceConfiguration(
                DeviceConfiguration.builder()
                    .eatonConfiguration(EatonConfiguration.builder()
                        .dataPoint(1)
                        .eatonGateway(EatonGateway.LIGHTS)
                        .build())
                    .build()
            )
            .build();

        when(repository.existsByIotVendorAndDataPointAndEatonGateway(any(), any(), any()))
            .thenReturn(Mono.just(Boolean.TRUE));

        sut.addDeviceConfiguration(request)
            .as(StepVerifier::create)
            .expectErrorMatches(throwable -> throwable instanceof InvalidDeviceConfigurationException
                && throwable.getMessage().equals("Configuration exist in database"))
            .verify();

        verify(repository, times(1))
            .existsByIotVendorAndDataPointAndEatonGateway(any(), any(), any());

        verify(repository, Mockito.never()).save(any());
    }

    @Test
    void should_throw_InvalidDeviceConfigurationRequestException_when_configuration_missing() {
        DeviceConfigurationRequest request = DeviceConfigurationRequest.builder()
            .roomName("roomName")
            .deviceType(DeviceType.TEMPERATURE_SENSOR.getValue())
            .iotVendor(IotVendor.EATON.getValue())
            .build();

        sut.addDeviceConfiguration(request)
            .as(StepVerifier::create)
            .expectErrorMatches(throwable ->
                throwable instanceof InvalidDeviceConfigurationException
                    && throwable.getMessage().contains("Device configuration is missing")
            )
            .verify();
    }

    @Test
    void should_throw_InvalidDeviceConfigurationRequestException_when_unknown_configuration_provided() {
        DeviceConfigurationRequest request = DeviceConfigurationRequest.builder()
            .roomName("roomName")
            .deviceType(DeviceType.TEMPERATURE_SENSOR.getValue())
            .iotVendor(IotVendor.EATON.getValue())
            .deviceConfiguration(
                DeviceConfiguration.builder()
                    .eatonConfiguration(EatonConfiguration.builder().build())
                    .build()
            )
            .build();

        sut.addDeviceConfiguration(request)
            .as(StepVerifier::create)
            .expectErrorMatches(throwable ->
                throwable instanceof InvalidDeviceConfigurationException
                    && throwable.getMessage().contains("Unknown device configuration")
            )
            .verify();
    }

    @Test
    void should_find_eaton_device_when_valid_configuration_provided() {

        when(repository.findDeviceConfigurationEntityByDataPointAndEatonGateway(any(), any()))
            .thenReturn(Mono.just(DeviceConfigurationEntity.builder()
                .id(1L)
                .createdAt(LocalDateTime.now())
                .roomName(RoomName.LIVING_ROOM)
                .deviceType(DeviceType.TEMPERATURE_SENSOR)
                .dataPoint(1)
                .eatonGateway(EatonGateway.LIGHTS)
                .build()));

        sut.getDeviceConfigurationByDataPointAndGateway(1, EatonGateway.BLINDS.getValue())
            .as(StepVerifier::create)
            .expectNext(EatonConfigurationResponse.builder()
                .roomName(RoomName.LIVING_ROOM)
                .deviceType(DeviceType.TEMPERATURE_SENSOR)
                .dataPoint(1)
                .build())
            .verifyComplete();
    }
}