package cloud.cholewa.data.device.eaton.service;

import cloud.cholewa.data.device.eaton.mapper.EatonConfigurationMapper;
import cloud.cholewa.data.device.registry.model.DeviceEntity;
import cloud.cholewa.data.device.registry.repository.DeviceRepository;
import cloud.cholewa.data.error.DeviceConfigurationExistsException;
import cloud.cholewa.data.error.DeviceConfigurationNotFoundException;
import cloud.cholewa.data.error.InvalidDeviceConfigurationException;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.EatonConfigurationResponse;
import cloud.cholewa.home.model.EatonDeviceConfiguration;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartDeviceType;
import cloud.cholewa.home.model.SmartVendor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static cloud.cholewa.home.model.EatonGatewayType.BLINDS;
import static cloud.cholewa.home.model.EatonGatewayType.LIGHTS;
import static cloud.cholewa.home.model.RoomName.LIVING_ROOM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

//The Eaton configuration on the rows of the device registry. The mapper is the real one: what a
//configuration becomes as a device, and what a device reads back as, is half of the subject.
@ExtendWith(MockitoExtension.class)
class EatonDeviceConfigurationServiceTest {

    private static final EatonDeviceConfiguration EATON_DEVICE_CONFIGURATION = EatonDeviceConfiguration.builder()
        .room(RoomName.BEDROOM)
        .point(1)
        .type(SmartDeviceType.TEMPERATURE_SENSOR)
        .gateway(BLINDS)
        .build();

    @Mock
    private DeviceRepository repository;

    @Spy
    private EatonConfigurationMapper mapper = new EatonConfigurationMapper();

    @InjectMocks
    private EatonDeviceConfigurationService sut;

    //a configuration is stored as a device of the registry: an Eaton device, switched on, bound to
    //its point, named after its room, its kind and its point
    @Test
    void should_add_a_configuration_as_a_device_of_the_registry() {
        when(repository.existsByEatonPoint(BLINDS, 1)).thenReturn(Mono.just(false));
        when(repository.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        sut.add(EATON_DEVICE_CONFIGURATION)
            .as(StepVerifier::create)
            .verifyComplete();

        final ArgumentCaptor<DeviceEntity> stored = ArgumentCaptor.forClass(DeviceEntity.class);
        verify(repository).insert(stored.capture());
        assertThat(stored.getValue().getId()).isNull();
        assertThat(stored.getValue().getCreatedAt()).isNotNull();
        assertThat(stored.getValue())
            .usingRecursiveComparison()
            .ignoringFields("createdAt")
            .isEqualTo(DeviceEntity.builder()
                .name("Bedroom temperature sensor 1")
                .room(RoomName.BEDROOM)
                .kind(DeviceKind.TEMPERATURE_SENSOR)
                .vendor(SmartVendor.EATON)
                .enabled(true)
                .eatonGateway(BLINDS)
                .eatonPoint(1)
                .openingSensors(List.of())
                .build());
    }

    @Test
    void should_report_configuration_registered_by_a_concurrent_request() {
        //both requests passed the existence check; the unique constraint (V13) catches the second one
        when(repository.existsByEatonPoint(any(), any(Integer.class))).thenReturn(Mono.just(false));
        when(repository.insert(any())).thenReturn(Mono.error(new DuplicateKeyException(
            "duplicate key value violates unique constraint \"devices_eaton_gateway_point_uq\"")));

        sut.add(EATON_DEVICE_CONFIGURATION)
            .as(StepVerifier::create)
            .expectError(DeviceConfigurationExistsException.class)
            .verify();
    }

    @Test
    void should_pass_through_duplicate_key_of_another_constraint() {
        when(repository.existsByEatonPoint(any(), any(Integer.class))).thenReturn(Mono.just(false));
        when(repository.insert(any())).thenReturn(Mono.error(new DuplicateKeyException("some_other_uq")));

        sut.add(EATON_DEVICE_CONFIGURATION)
            .as(StepVerifier::create)
            .expectError(DuplicateKeyException.class)
            .verify();
    }

    @Test
    void should_throw_exception_when_configuration_exists() {
        when(repository.existsByEatonPoint(any(), any(Integer.class))).thenReturn(Mono.just(true));

        sut.add(EATON_DEVICE_CONFIGURATION)
            .as(StepVerifier::create)
            .expectErrorMatches(throwable -> throwable instanceof DeviceConfigurationExistsException
                && throwable.getMessage().equals("Configuration exist in database"))
            .verify();

        verify(repository).existsByEatonPoint(BLINDS, 1);
        verify(repository, never()).existsByEatonPoint(LIGHTS, 1);
        verify(repository, never()).insert(any());
    }

    //the registry has no kind for "other": refused before anything is asked of the database
    @Test
    void should_refuse_a_type_the_registry_has_no_kind_for() {
        final EatonDeviceConfiguration other = EatonDeviceConfiguration.builder()
            .room(RoomName.BEDROOM)
            .point(1)
            .type(SmartDeviceType.OTHER)
            .gateway(BLINDS)
            .build();

        sut.add(other)
            .as(StepVerifier::create)
            .expectErrorSatisfies(throwable ->
                Assertions.assertThat(throwable)
                    .isInstanceOf(InvalidDeviceConfigurationException.class)
                    .hasMessage("A device of this type cannot be registered: other")
            )
            .verify();

        verifyNoInteractions(repository);
    }

    @Test
    void should_throw_exception_when_configuration_not_exists() {
        when(repository.findByEatonPoint(any(), any(Integer.class))).thenReturn(Mono.empty());

        sut.get(1, "blinds")
            .as(StepVerifier::create)
            .expectErrorSatisfies(throwable ->
                Assertions.<Throwable>assertThat(throwable)
                    .isInstanceOf(DeviceConfigurationNotFoundException.class)
                    .hasMessage("Device not found for point: 1 on gateway: blinds")
            )
            .verify();

        verifyNoMoreInteractions(repository);
    }

    //amx-service sends the gateway in capitals; the answer is the configuration it always got -
    //the point, the type and the room - read off a device of the registry
    @ParameterizedTest
    @ValueSource(strings = {"blinds", "BLINDS"})
    void should_find_eaton_device_when_configuration_exists(final String gateway) {
        when(repository.findByEatonPoint(BLINDS, 1)).thenReturn(Mono.just(DeviceEntity.builder()
            .id(5L)
            .createdAt(LocalDateTime.of(2026, 10, 12, 12, 0))
            .name("Living room temperature sensor 1")
            .room(LIVING_ROOM)
            .kind(DeviceKind.TEMPERATURE_SENSOR)
            .vendor(SmartVendor.EATON)
            .enabled(true)
            .eatonGateway(BLINDS)
            .eatonPoint(1)
            .build()));

        sut.get(1, gateway)
            .as(StepVerifier::create)
            .expectNext(EatonConfigurationResponse.builder()
                .point(1)
                .type(SmartDeviceType.TEMPERATURE_SENSOR)
                .room(LIVING_ROOM)
                .build())
            .verifyComplete();
    }

    //a device of the registry on a data point, of a kind amx-service has no type for: to the
    //lookup that point is not configured
    @Test
    void should_report_a_device_of_a_kind_the_eaton_vocabulary_lacks_as_not_configured() {
        when(repository.findByEatonPoint(BLINDS, 1)).thenReturn(Mono.just(DeviceEntity.builder()
            .id(6L)
            .name("Office window sensor")
            .room(RoomName.OFFICE)
            .kind(DeviceKind.OPENING_SENSOR)
            .vendor(SmartVendor.EATON)
            .enabled(true)
            .eatonGateway(BLINDS)
            .eatonPoint(1)
            .build()));

        sut.get(1, "blinds")
            .as(StepVerifier::create)
            .verifyError(DeviceConfigurationNotFoundException.class);
    }

    @Test
    void should_throw_exception_for_unknown_gateway() {
        sut.get(1, "garden")
            .as(StepVerifier::create)
            .expectErrorSatisfies(throwable ->
                Assertions.assertThat(throwable)
                    .isInstanceOf(InvalidDeviceConfigurationException.class)
                    .hasMessage("Unknown Eaton gateway: garden")
            )
            .verify();

        verifyNoInteractions(repository);
    }
}
