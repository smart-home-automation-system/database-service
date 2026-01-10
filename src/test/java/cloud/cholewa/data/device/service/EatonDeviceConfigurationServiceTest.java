package cloud.cholewa.data.device.service;

import cloud.cholewa.data.device.eaton.repository.EatonDeviceConfigurationRepository;
import cloud.cholewa.data.device.eaton.service.EatonDeviceConfigurationService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EatonDeviceConfigurationServiceTest {

    @Mock
    private EatonDeviceConfigurationRepository repository;

    @InjectMocks
    private EatonDeviceConfigurationService sut;

//    @Test
//    void should_add_device_configuration() {
//        DeviceConfigurationRequest request = DeviceConfigurationRequest.builder()
//            .roomName(RoomName.BEDROOM.getValue())
//            .deviceType(DeviceType.TEMPERATURE_SENSOR.getValue())
//            .iotVendor(IotVendor.EATON.getValue())
//            .deviceConfiguration(
//                DeviceConfiguration.builder()
//                    .eatonConfiguration(EatonConfiguration.builder()
//                        .dataPoint(1)
//                        .eatonGateway(EatonGateway.LIGHTS)
//                        .build())
//                    .build()
//            )
//            .build();
//
//        when(repository.existsByIotVendorAndDataPointAndEatonGateway(any(), any(), any()))
//            .thenReturn(Mono.just(false));
//
//        when(repository.save(any())).thenReturn(Mono.just(DeviceConfigurationEntity.builder().build()));
//
//        sut.addDeviceConfiguration(request)
//            .as(StepVerifier::create)
//            .verifyComplete();
//
//        verify(repository, times(1))
//            .existsByIotVendorAndDataPointAndEatonGateway(any(), any(), any());
//
//        verify(repository, times(1)).save(any());
//    }
//
//    @Test
//    void should_not_add_device_configuration_when_device_exists() {
//        DeviceConfigurationRequest request = DeviceConfigurationRequest.builder()
//            .roomName("roomName")
//            .deviceType(DeviceType.TEMPERATURE_SENSOR.getValue())
//            .iotVendor(IotVendor.EATON.getValue())
//            .deviceConfiguration(
//                DeviceConfiguration.builder()
//                    .eatonConfiguration(EatonConfiguration.builder()
//                        .dataPoint(1)
//                        .eatonGateway(EatonGateway.LIGHTS)
//                        .build())
//                    .build()
//            )
//            .build();
//
//        when(repository.existsByIotVendorAndDataPointAndEatonGateway(any(), any(), any()))
//            .thenReturn(Mono.just(Boolean.TRUE));
//
//        sut.addDeviceConfiguration(request)
//            .as(StepVerifier::create)
//            .expectErrorMatches(throwable -> throwable instanceof InvalidDeviceConfigurationException
//                && throwable.getMessage().equals("Configuration exist in database"))
//            .verify();
//
//        verify(repository, times(1))
//            .existsByIotVendorAndDataPointAndEatonGateway(any(), any(), any());
//
//        verify(repository, Mockito.never()).save(any());
//    }
//
//    @Test
//    void should_throw_InvalidDeviceConfigurationRequestException_when_configuration_missing() {
//        DeviceConfigurationRequest request = DeviceConfigurationRequest.builder()
//            .roomName("roomName")
//            .deviceType(DeviceType.TEMPERATURE_SENSOR.getValue())
//            .iotVendor(IotVendor.EATON.getValue())
//            .build();
//
//        sut.addDeviceConfiguration(request)
//            .as(StepVerifier::create)
//            .expectErrorMatches(throwable ->
//                throwable instanceof InvalidDeviceConfigurationException
//                    && throwable.getMessage().contains("Device configuration is missing")
//            )
//            .verify();
//    }
//
//    @Test
//    void should_throw_InvalidDeviceConfigurationRequestException_when_unknown_configuration_provided() {
//        DeviceConfigurationRequest request = DeviceConfigurationRequest.builder()
//            .roomName("roomName")
//            .deviceType(DeviceType.TEMPERATURE_SENSOR.getValue())
//            .iotVendor(IotVendor.EATON.getValue())
//            .deviceConfiguration(
//                DeviceConfiguration.builder()
//                    .eatonConfiguration(EatonConfiguration.builder().build())
//                    .build()
//            )
//            .build();
//
//        sut.addDeviceConfiguration(request)
//            .as(StepVerifier::create)
//            .expectErrorMatches(throwable ->
//                throwable instanceof InvalidDeviceConfigurationException
//                    && throwable.getMessage().contains("Unknown device configuration")
//            )
//            .verify();
//    }
//
//    @Test
//    void should_find_eaton_device_when_valid_configuration_provided() {
//
//        when(repository.findDeviceConfigurationEntityByDataPointAndEatonGateway(any(), any()))
//            .thenReturn(Mono.just(DeviceConfigurationEntity.builder()
//                .id(1L)
//                .createdAt(LocalDateTime.now())
//                .roomName(RoomName.LIVING_ROOM)
//                .deviceType(DeviceType.TEMPERATURE_SENSOR)
//                .dataPoint(1)
//                .eatonGateway(EatonGateway.LIGHTS)
//                .build()));
//
//        sut.get(1, EatonGateway.BLINDS.getValue())
//            .as(StepVerifier::create)
//            .expectNext(EatonConfigurationResponse.builder()
//                .roomName(RoomName.LIVING_ROOM)
//                .deviceType(DeviceType.TEMPERATURE_SENSOR)
//                .dataPoint(1)
//                .build())
//            .verifyComplete();
//    }
}
