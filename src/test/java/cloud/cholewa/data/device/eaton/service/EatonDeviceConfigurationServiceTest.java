package cloud.cholewa.data.device.eaton.service;

import cloud.cholewa.data.device.eaton.mapper.EatonDeviceConfigurationMapper;
import cloud.cholewa.data.device.eaton.model.EatonDeviceConfigurationEntity;
import cloud.cholewa.data.device.eaton.repository.EatonDeviceConfigurationRepository;
import cloud.cholewa.data.error.DeviceConfigurationNotFoundException;
import cloud.cholewa.data.error.InvalidDeviceConfigurationException;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import cloud.cholewa.home.model.EatonDeviceConfiguration;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartDeviceType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static cloud.cholewa.home.model.EatonGatewayType.BLINDS;
import static cloud.cholewa.home.model.EatonGatewayType.LIGHTS;
import static cloud.cholewa.home.model.RoomName.LIVING_ROOM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EatonDeviceConfigurationServiceTest {

    private static final EatonDeviceConfiguration EATON_DEVICE_CONFIGURATION = EatonDeviceConfiguration.builder()
        .room(RoomName.BEDROOM)
        .point(1)
        .type(SmartDeviceType.TEMPERATURE_SENSOR)
        .gateway(BLINDS)
        .build();

    @Mock
    private EatonDeviceConfigurationRepository repository;

    @Mock
    private EatonDeviceConfigurationMapper mapper;

    @InjectMocks
    private EatonDeviceConfigurationService sut;

    @Test
    void should_add_device_configuration_when_configuration_not_exists() {
        when(repository.existsByPointAndGateway(anyInt(), any())).thenReturn(Mono.just(false));

        when(repository.save(any())).thenReturn(Mono.just(EatonDeviceConfigurationEntity.builder().build()));

        when(mapper.toEntity(any())).thenReturn(EatonDeviceConfigurationEntity.builder().build());

        sut.add(EATON_DEVICE_CONFIGURATION)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(repository, times(1)).existsByPointAndGateway(anyInt(), any());
        verify(repository, times(1)).save(any());
        verify(mapper, times(1)).toEntity(any());
    }

    @Test
    void should_throw_exception_when_configuration_exists() {
        when(repository.existsByPointAndGateway(anyInt(), any())).thenReturn(Mono.just(true));

        sut.add(EATON_DEVICE_CONFIGURATION)
            .as(StepVerifier::create)
            .expectErrorMatches(throwable -> throwable instanceof InvalidDeviceConfigurationException
                && throwable.getMessage().equals("Configuration exist in database"))
            .verify();

        verify(repository, times(1)).existsByPointAndGateway(anyInt(), eq(BLINDS));
        verify(repository, never()).existsByPointAndGateway(anyInt(), eq(LIGHTS));
        verify(repository, never()).save(any());
        verify(mapper, never()).toEntity(any());
    }

    @Test
    void should_throw_exception_when_configuration_not_exists() {
        when(repository.findByPointAndGateway(anyInt(), any())).thenReturn(Mono.empty());

        sut.get(1, "blinds")
            .as(StepVerifier::create)
            .expectErrorSatisfies(throwable ->
                Assertions.<Throwable>assertThat(throwable)
                    .isInstanceOf(DeviceConfigurationNotFoundException.class)
                    .hasMessageContaining("Device not found")
            )
            .verify();

        verifyNoInteractions(mapper);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void should_find_eaton_device_when_configuration_exists() {
        when(repository.findByPointAndGateway(anyInt(), any()))
            .thenReturn(Mono.just(EatonDeviceConfigurationEntity.builder().point(1).room(LIVING_ROOM).build()));

        when(mapper.toResponse(any()))
            .thenReturn(EatonConfigurationResponse.builder().room(LIVING_ROOM).build());

        sut.get(1, "blinds")
            .as(StepVerifier::create)
            .expectNextCount(1)
            .verifyComplete();

        verify(repository, times(1)).findByPointAndGateway(anyInt(), any());
        verify(mapper, times(1)).toResponse(any());
    }
}
