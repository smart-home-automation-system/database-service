package cloud.cholewa.data.device.registry.service;

import cloud.cholewa.data.device.registry.mapper.SmartDeviceMapper;
import cloud.cholewa.data.device.registry.model.DeviceEntity;
import cloud.cholewa.data.device.registry.repository.DeviceRepository;
import cloud.cholewa.data.error.DeviceNotFoundException;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.EatonGatewayType;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartDevice;
import cloud.cholewa.home.model.SmartVendor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceRegistryServiceTest {

    @Mock
    private DeviceRepository repository;

    @Spy
    private SmartDeviceMapper mapper = new SmartDeviceMapper();

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
