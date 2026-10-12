package cloud.cholewa.data.device.registry.service;

import cloud.cholewa.data.device.registry.mapper.SmartDeviceMapper;
import cloud.cholewa.data.device.registry.repository.DeviceRepository;
import cloud.cholewa.data.error.DeviceNotFoundException;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartDevice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceRegistryService {

    //By the room and the name as a client reads them - the values of the SDK, not the constants
    //the rows hold - the name whatever its case, and the id last, so that two devices of one
    //name in one room keep their order from one call to the next. SmartDevice is not Comparable
    private static final Comparator<SmartDevice> BY_ROOM_AND_NAME = Comparator
        .comparing((SmartDevice device) -> device.getRoom().getValue())
        .thenComparing(SmartDevice::getName, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(SmartDevice::getId);

    private final DeviceRepository repository;
    private final SmartDeviceMapper mapper;

    //Every filter is optional, and each is the value a client sends (opening sensor, living
    //room). An empty answer is an empty list: the services poll this, and nothing registered
    //for a room is not an error
    public Mono<List<SmartDevice>> find(final String kind, final String room, final Boolean enabled) {
        return Mono.fromCallable(() -> new Filter(
                parsed(kind, DeviceKind::fromValue, "device kind"),
                parsed(room, RoomName::fromValue, "room")
            ))
            .flatMapMany(filter -> repository.findAll(filter.kind(), filter.room(), enabled))
            .map(mapper::toDevice)
            .collectSortedList(BY_ROOM_AND_NAME);
    }

    public Mono<SmartDevice> get(final long id) {
        return repository.findById(id)
            .map(mapper::toDevice)
            .switchIfEmpty(Mono.error(() -> DeviceNotFoundException.forId(id)));
    }

    //A filter left empty is a filter not given - "kind=" is what a form writes for a field
    //nobody filled, and Spring reads an empty enabled as none the same way. A value that is no
    //value of the enum is a bad request, answered like a parameter of the wrong type - 400
    //without a code, the way an unknown room in a body is. The case does not matter: the
    //enums of the SDK read their values ignoring it
    private static <T> T parsed(final String value, final Function<String, T> parser, final String what) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return parser.apply(value);
        } catch (IllegalArgumentException e) {
            throw new ServerWebInputException("Unknown " + what + ": " + value);
        }
    }

    private record Filter(DeviceKind kind, RoomName room) {
    }
}
