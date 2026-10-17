package cloud.cholewa.data.device.registry.service;

import cloud.cholewa.data.device.registry.mapper.DeviceEntityMapper;
import cloud.cholewa.data.device.registry.mapper.SmartDeviceMapper;
import cloud.cholewa.data.device.registry.model.DeviceEntity;
import cloud.cholewa.data.device.registry.repository.DeviceRepository;
import cloud.cholewa.data.error.DeviceConflictException;
import cloud.cholewa.data.error.DeviceNotFoundException;
import cloud.cholewa.data.error.InvalidDeviceException;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.EatonBinding;
import cloud.cholewa.home.model.EatonGatewayType;
import cloud.cholewa.home.model.LightKind;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartDevice;
import cloud.cholewa.home.model.SmartVendor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceRegistryServiceTest {

    @Mock
    private DeviceRepository repository;

    @Spy
    private SmartDeviceMapper mapper = new SmartDeviceMapper();

    @Spy
    private DeviceEntityMapper entityMapper = new DeviceEntityMapper();

    @InjectMocks
    private DeviceRegistryService sut;

    @Test
    void should_ask_for_everything_when_no_filter_is_given() {
        when(repository.findAll(null, null, null)).thenReturn(Flux.just(device(1L, "Lamp", RoomName.OFFICE)));

        sut.find(null, null, null).as(StepVerifier::create)
            .assertNext(devices -> assertThat(devices).extracting(SmartDevice::getName).containsExactly("Lamp"))
            .verifyComplete();
    }

    //the filters arrive as a client writes them - the values of the SDK, in any case
    @Test
    void should_read_the_filters_by_the_values_a_client_sends() {
        when(repository.findAll(DeviceKind.OPENING_SENSOR, RoomName.LIVING_ROOM, true)).thenReturn(Flux.empty());

        sut.find("opening sensor", "Living Room", true).as(StepVerifier::create)
            .expectNext(List.of())
            .verifyComplete();
    }

    //By the room and the name as a client reads them: "bathroom up" before "office" although
    //OFFICE comes first among the constants, the names whatever their case, and two devices of
    //one name by their id - so the order of an answer never depends on the order of the rows
    @Test
    void should_sort_by_the_room_and_then_by_the_name_whatever_its_case() {
        when(repository.findAll(any(), any(), any())).thenReturn(Flux.just(
            device(1L, "window", RoomName.OFFICE),
            device(2L, "Desk lamp", RoomName.OFFICE),
            device(3L, "Mirror", RoomName.BATHROOM_UP),
            device(5L, "ceiling", RoomName.OFFICE),
            device(4L, "ceiling", RoomName.OFFICE)
        ));

        sut.find(null, null, null).as(StepVerifier::create)
            .assertNext(devices -> assertThat(devices).extracting(SmartDevice::getId).containsExactly(3L, 4L, 5L, 2L, 1L))
            .verifyComplete();
    }

    //"kind=&room=" is what a form sends for fields nobody filled
    @Test
    void should_read_an_empty_filter_as_no_filter() {
        when(repository.findAll(null, null, null)).thenReturn(Flux.empty());

        sut.find("", "  ", null).as(StepVerifier::create)
            .expectNext(List.of())
            .verifyComplete();
    }

    //nothing registered is an answer, not an error: the services poll the registry
    @Test
    void should_answer_an_empty_list_when_nothing_matches() {
        when(repository.findAll(any(), any(), any())).thenReturn(Flux.empty());

        sut.find(null, "garden", null).as(StepVerifier::create)
            .expectNext(List.of())
            .verifyComplete();
    }

    //a filter that is no value of its enum is a bad request, refused before any query runs
    @Test
    void should_refuse_a_kind_it_does_not_know() {
        sut.find("thermostat", null, null).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(ServerWebInputException.class)
                .hasMessageContaining("Unknown device kind: thermostat"));

        verifyNoInteractions(repository);
    }

    //the name of a constant is not a value: OPENING_SENSOR is what the row holds, not what a
    //client may ask with
    @Test
    void should_refuse_the_name_of_a_constant_as_a_filter() {
        sut.find("OPENING_SENSOR", null, null).as(StepVerifier::create)
            .verifyError(ServerWebInputException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void should_refuse_a_room_it_does_not_know() {
        sut.find(null, "attic", null).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(ServerWebInputException.class)
                .hasMessageContaining("Unknown room: attic"));

        verifyNoInteractions(repository);
    }

    @Test
    void should_return_a_device_by_its_id() {
        when(repository.findById(7L)).thenReturn(Mono.just(device(7L, "Lamp", RoomName.OFFICE)));

        sut.get(7L).as(StepVerifier::create)
            .assertNext(device -> {
                assertThat(device.getId()).isEqualTo(7L);
                assertThat(device.getName()).isEqualTo("Lamp");
                assertThat(device.getEaton().getPoint()).isEqualTo(7);
            })
            .verifyComplete();
    }

    @Test
    void should_report_a_device_that_is_not_registered() {
        when(repository.findById(7L)).thenReturn(Mono.empty());

        sut.get(7L).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(DeviceNotFoundException.class)
                .hasMessage("Device not found for id: 7"));
    }

    //--- the writes

    @Test
    void should_register_a_device_and_answer_it_with_the_id_the_registry_gave_it() {
        when(repository.insert(any())).thenAnswer(stored ->
            Mono.just(stored.<DeviceEntity>getArgument(0).toBuilder().id(31L).build()));

        sut.add(light(999L, "Kitchen ceiling", 44)).as(StepVerifier::create)
            .assertNext(device -> {
                assertThat(device.getId()).isEqualTo(31L);
                assertThat(device.getName()).isEqualTo("Kitchen ceiling");
                assertThat(device.getEaton().getPoint()).isEqualTo(44);
            })
            .verifyComplete();

        //the id of the payload never reaches the row
        final ArgumentCaptor<DeviceEntity> row = ArgumentCaptor.forClass(DeviceEntity.class);
        verify(repository).insert(row.capture());
        assertThat(row.getValue().getId()).isNull();
    }

    //a device the rules refuse runs no query
    @Test
    void should_refuse_a_device_the_rules_refuse_before_anything_is_asked_of_the_database() {
        final SmartDevice device = lightBuilder(null, "Kitchen ceiling", 44).vendor(SmartVendor.SHELLY).build();

        sut.add(device).as(StepVerifier::create).verifyError(InvalidDeviceException.class);
        sut.replace(7L, device).as(StepVerifier::create).verifyError(InvalidDeviceException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void should_report_a_taken_binding_as_a_conflict_by_the_index_that_refused_it() {
        when(repository.insert(any())).thenReturn(Mono.error(new DuplicateKeyException(
            "duplicate key value violates unique constraint \"devices_eaton_gateway_point_uq\"")));

        sut.add(light(null, "Kitchen ceiling", 44)).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(DeviceConflictException.class)
                .hasMessage("A device is already registered for the point [44] of the Eaton gateway [lights]"));
    }

    //an index this service does not know is not worded as a taken binding: the library answers it
    @Test
    void should_leave_a_duplicate_it_cannot_name_as_it_is() {
        when(repository.insert(any())).thenReturn(Mono.error(new DuplicateKeyException(
            "duplicate key value violates unique constraint \"devices_something_new_uq\"")));

        sut.add(light(null, "Kitchen ceiling", 44)).as(StepVerifier::create)
            .verifyError(DuplicateKeyException.class);
    }

    @Test
    void should_replace_a_device_under_its_id_whatever_id_the_payload_carries() {
        when(repository.findById(7L)).thenReturn(Mono.just(device(7L, "Lamp", RoomName.OFFICE)));
        when(repository.update(any())).thenAnswer(stored -> Mono.just(stored.getArgument(0)));

        sut.replace(7L, light(999L, "Desk lamp", 8)).as(StepVerifier::create)
            .assertNext(device -> {
                assertThat(device.getId()).isEqualTo(7L);
                assertThat(device.getName()).isEqualTo("Desk lamp");
                assertThat(device.getEaton().getPoint()).isEqualTo(8);
            })
            .verifyComplete();
    }

    //the services keep what they know of a device under its id: blinds stay blinds
    @Test
    void should_refuse_to_make_a_device_one_of_another_kind() {
        when(repository.findById(7L)).thenReturn(Mono.just(device(7L, "Lamp", RoomName.OFFICE)));
        final SmartDevice blinds = lightBuilder(null, "Window", 7).kind(DeviceKind.BLINDS).lightKind(null).build();

        sut.replace(7L, blinds).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(InvalidDeviceException.class)
                .hasMessageStartingWith("The kind of a device cannot be changed: device 7 is of the kind [light]"));

        verify(repository, never()).update(any());
    }

    @Test
    void should_report_a_device_that_is_not_registered_to_every_write() {
        when(repository.findById(7L)).thenReturn(Mono.empty());

        sut.replace(7L, light(null, "Desk lamp", 8)).as(StepVerifier::create).verifyError(DeviceNotFoundException.class);
        sut.enable(7L).as(StepVerifier::create).verifyError(DeviceNotFoundException.class);
        sut.disable(7L).as(StepVerifier::create).verifyError(DeviceNotFoundException.class);
        sut.remove(7L).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(DeviceNotFoundException.class)
                .hasMessage("Device not found for id: 7"));

        verify(repository, never()).update(any());
    }

    @Test
    void should_switch_a_device_off_and_on() {
        when(repository.findById(7L)).thenReturn(Mono.just(device(7L, "Lamp", RoomName.OFFICE)));
        when(repository.update(any())).thenAnswer(stored -> Mono.just(stored.getArgument(0)));

        sut.disable(7L).as(StepVerifier::create)
            .assertNext(device -> {
                assertThat(device.getEnabled()).isFalse();
                assertThat(device.getName()).isEqualTo("Lamp");
            })
            .verifyComplete();
        sut.enable(7L).as(StepVerifier::create)
            .assertNext(device -> assertThat(device.getEnabled()).isTrue())
            .verifyComplete();
    }

    @Test
    void should_remove_a_device() {
        when(repository.findById(7L)).thenReturn(Mono.just(device(7L, "Lamp", RoomName.OFFICE)));
        when(repository.findAll(DeviceKind.BLINDS, null, null)).thenReturn(Flux.just(blinds(9L, List.of(31L))));
        when(repository.deleteById(7L)).thenReturn(Mono.just(1L));

        sut.remove(7L).as(StepVerifier::create).verifyComplete();
    }

    //--- the opening sensors of blinds (HAS-290)

    @Test
    void should_register_blinds_with_the_opening_sensors_of_their_window() {
        when(repository.findAllById(List.of(31L, 30L))).thenReturn(Flux.just(sensor(30L), sensor(31L)));
        when(repository.insert(any())).thenAnswer(stored ->
            Mono.just(stored.<DeviceEntity>getArgument(0).toBuilder().id(9L).build()));

        sut.add(blindsDevice(List.of(31L, 30L))).as(StepVerifier::create)
            .assertNext(device -> assertThat(device.getOpeningSensors()).containsExactly(31L, 30L))
            .verifyComplete();
    }

    //an id nobody registered and a device of another kind are both no sensor of the window
    @Test
    void should_refuse_opening_sensors_that_are_no_registered_opening_sensor() {
        when(repository.findAllById(List.of(31L, 7L, 40L))).thenReturn(Flux.just(sensor(31L), device(7L, "Lamp", RoomName.OFFICE)));

        sut.add(blindsDevice(List.of(31L, 7L, 40L))).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(InvalidDeviceException.class)
                .hasMessage("openingSensors name devices that are no registered opening sensor: [7, 40]"));

        verify(repository, never()).insert(any());
    }

    @Test
    void should_check_the_opening_sensors_of_replaced_blinds_too() {
        when(repository.findById(9L)).thenReturn(Mono.just(blinds(9L, List.of())));
        when(repository.findAllById(List.of(40L))).thenReturn(Flux.empty());

        sut.replace(9L, blindsDevice(List.of(40L))).as(StepVerifier::create)
            .verifyError(InvalidDeviceException.class);

        verify(repository, never()).update(any());
    }

    //blinds without a sensor ask nothing about sensors
    @Test
    void should_not_look_for_sensors_of_blinds_without_any() {
        when(repository.insert(any())).thenAnswer(stored -> Mono.just(stored.getArgument(0)));

        sut.add(blindsDevice(List.of())).as(StepVerifier::create)
            .assertNext(device -> assertThat(device.getOpeningSensors()).isNull())
            .verifyComplete();

        verify(repository, never()).findAllById(any());
    }

    //the window would read as closed from then on without a word: the link goes first
    @Test
    void should_refuse_to_remove_an_opening_sensor_blinds_still_name() {
        when(repository.findById(31L)).thenReturn(Mono.just(sensor(31L)));
        when(repository.findAll(DeviceKind.BLINDS, null, null))
            .thenReturn(Flux.just(blinds(9L, List.of(30L, 31L)), blinds(10L, List.of()), blinds(12L, List.of(31L))));

        sut.remove(31L).as(StepVerifier::create)
            .verifyErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(DeviceConflictException.class)
                .hasMessage("The device 31 is the opening sensor of the blinds 9 [Window 9], 12 [Window 12]"
                    + " - remove it from their openingSensors first"));

        verify(repository, never()).deleteById(31L);
    }

    private static SmartDevice blindsDevice(final List<Long> sensors) {
        return SmartDevice.builder()
            .name("Cinema windows")
            .room(RoomName.CINEMA)
            .kind(DeviceKind.BLINDS)
            .vendor(SmartVendor.EATON)
            .enabled(true)
            .eaton(EatonBinding.builder().gateway(EatonGatewayType.BLINDS).point(20).build())
            .openingSensors(sensors)
            .build();
    }

    private static DeviceEntity blinds(final long id, final List<Long> sensors) {
        return device(id, "Window " + id, RoomName.CINEMA).toBuilder()
            .kind(DeviceKind.BLINDS)
            .eatonGateway(EatonGatewayType.BLINDS)
            .openingSensors(sensors)
            .build();
    }

    private static DeviceEntity sensor(final long id) {
        return device(id, "Window sensor " + id, RoomName.CINEMA).toBuilder()
            .kind(DeviceKind.OPENING_SENSOR)
            .vendor(SmartVendor.SHELLY)
            .eatonGateway(null)
            .eatonPoint(null)
            .bthomeAddress("7c:c6:b6:00:00:" + id)
            .build();
    }

    private static SmartDevice light(final Long id, final String name, final int point) {
        return lightBuilder(id, name, point).build();
    }

    private static SmartDevice.SmartDeviceBuilder<?, ?> lightBuilder(final Long id, final String name, final int point) {
        return SmartDevice.builder()
            .id(id)
            .name(name)
            .room(RoomName.KITCHEN)
            .kind(DeviceKind.LIGHT)
            .vendor(SmartVendor.EATON)
            .enabled(true)
            .eaton(EatonBinding.builder().gateway(EatonGatewayType.LIGHTS).point(point).build())
            .lightKind(LightKind.DIMMER);
    }

    private static DeviceEntity device(final long id, final String name, final RoomName room) {
        return DeviceEntity.builder()
            .id(id)
            .createdAt(LocalDateTime.of(2026, 10, 12, 12, 0))
            .name(name)
            .room(room)
            .kind(DeviceKind.LIGHT)
            .vendor(SmartVendor.EATON)
            .enabled(true)
            .eatonGateway(EatonGatewayType.LIGHTS)
            .eatonPoint((int) id)
            .build();
    }
}
