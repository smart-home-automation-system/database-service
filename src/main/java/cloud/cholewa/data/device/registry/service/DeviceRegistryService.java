package cloud.cholewa.data.device.registry.service;

import cloud.cholewa.data.device.registry.mapper.DeviceEntityMapper;
import cloud.cholewa.data.device.registry.mapper.SmartDeviceMapper;
import cloud.cholewa.data.device.registry.model.DeviceEntity;
import cloud.cholewa.data.device.registry.repository.DeviceRepository;
import cloud.cholewa.data.error.DeviceConflictException;
import cloud.cholewa.data.error.DeviceNotFoundException;
import cloud.cholewa.data.error.InvalidDeviceException;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.RoomName;
import cloud.cholewa.home.model.SmartDevice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
    //the unique indexes of V13, by which a duplicate tells what was taken; renamed in a
    //migration, a duplicate would be answered as the bare "Duplicate Key" of the library
    private static final String SHELLY_ADDRESS_UNIQUE_INDEX = "devices_shelly_address_channel_uq";
    private static final String SHELLY_DEVICE_ID_UNIQUE_INDEX = "devices_shelly_device_id_channel_uq";
    private static final String EATON_POINT_UNIQUE_CONSTRAINT = "devices_eaton_gateway_point_uq";
    private static final String BTHOME_ADDRESS_UNIQUE_CONSTRAINT = "devices_bthome_address_uq";

    private final SmartDeviceMapper mapper;
    private final DeviceEntityMapper entityMapper;

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

    //The rules first: a refused device runs no query. An id the payload carries is not read -
    //the model reads one that is sent, the registry gives its own.
    //A binding that is taken is found by the schema alone (the four unique indexes of V13):
    //the registry is not written by two at once, and a check before the insert would only be
    //the same four questions asked twice
    public Mono<SmartDevice> add(final SmartDevice device) {
        return Mono.fromCallable(() -> {
                DeviceRules.check(device);
                return entityMapper.toNewEntity(device);
            })
            .flatMap(entity -> openingSensorsRegistered(entity).thenReturn(entity))
            .flatMap(entity -> repository.insert(entity)
                .onErrorMap(DuplicateKeyException.class, e -> bindingTaken(e, entity)))
            .doOnNext(stored -> log.info("Registered device: {} [{}]", stored.getId(), stored.getName()))
            .map(mapper::toDevice);
    }

    //The whole device, replaced by what the payload says - enabled included: the model has no
    //default for it, so a payload cannot switch a device on by leaving the field out. What is
    //left out is gone: a binding, the way a light is driven.
    //The kind stays: the services keep what they know of a device under its id, and blinds
    //that come back as a light would be read with the schedules of blinds. A device of another
    //kind is another device - removed and registered again
    public Mono<SmartDevice> replace(final long id, final SmartDevice device) {
        return Mono.fromRunnable(() -> DeviceRules.check(device))
            .then(Mono.defer(() -> find(id)))
            .map(existing -> {
                if (existing.getKind() != device.getKind()) {
                    throw new InvalidDeviceException(
                        "The kind of a device cannot be changed: device " + id + " is of the kind ["
                            + existing.getKind().getValue() + "]. Remove it and register the new one");
                }
                return entityMapper.toReplacement(existing, device);
            })
            .flatMap(entity -> openingSensorsRegistered(entity).thenReturn(entity))
            .flatMap(entity -> repository.update(entity)
                .onErrorMap(DuplicateKeyException.class, e -> bindingTaken(e, entity)))
            .doOnNext(stored -> log.info("Replaced device: {} [{}]", stored.getId(), stored.getName()))
            .map(mapper::toDevice);
    }

    public Mono<SmartDevice> enable(final long id) {
        return changeEnabled(id, true);
    }

    public Mono<SmartDevice> disable(final long id) {
        return changeEnabled(id, false);
    }

    //Gone for good, and with it whatever the services kept under its id. For an Eaton device
    //that is also its configuration: amx-service gets a 404 for the data point from here on
    //An opening sensor that blinds still name is not removed: their window would read as closed
    //from then on without a word - the link goes first, with a PUT of the blinds
    public Mono<Void> remove(final long id) {
        return repository.findAll(DeviceKind.BLINDS, null, null)
            .filter(blinds -> blinds.getOpeningSensors() != null && blinds.getOpeningSensors().contains(id))
            .map(blinds -> blinds.getId() + " [" + blinds.getName() + "]")
            .collectList()
            .flatMap(users -> users.isEmpty()
                ? Mono.empty()
                : Mono.error(new DeviceConflictException(
                    "The device " + id + " is the opening sensor of the blinds " + String.join(", ", users)
                        + " - remove it from their openingSensors first")))
            .then(Mono.defer(() -> repository.deleteById(id)))
            .filter(removed -> removed > 0)
            .switchIfEmpty(Mono.error(() -> DeviceNotFoundException.forId(id)))
            .doOnNext(removed -> log.info("Removed device: {}", id))
            .then();
    }

    private Mono<SmartDevice> changeEnabled(final long id, final boolean enabled) {
        return find(id)
            .map(existing -> entityMapper.withEnabled(existing, enabled))
            .flatMap(repository::update)
            .doOnNext(stored -> log.info("Device: {} [{}] enabled: {}", stored.getId(), stored.getName(), enabled))
            .map(mapper::toDevice);
    }

    //Each id of openingSensors has to be a registered device of the kind opening sensor - the
    //rules have already kept the list to blinds, without a null or a repeat. A switched-off
    //sensor is still a sensor of that window
    private Mono<Void> openingSensorsRegistered(final DeviceEntity blinds) {
        final List<Long> ids = blinds.getOpeningSensors();
        if (ids.isEmpty()) {
            return Mono.empty();
        }
        return repository.findAllById(ids)
            .filter(device -> device.getKind() == DeviceKind.OPENING_SENSOR)
            .map(DeviceEntity::getId)
            .collectList()
            .flatMap(sensors -> {
                final List<Long> missing = ids.stream().filter(sensorId -> !sensors.contains(sensorId)).toList();
                return missing.isEmpty()
                    ? Mono.empty()
                    : Mono.error(new InvalidDeviceException(
                        "openingSensors name devices that are no registered opening sensor: " + missing));
            });
    }

    private Mono<DeviceEntity> find(final long id) {
        return repository.findById(id)
            .switchIfEmpty(Mono.error(() -> DeviceNotFoundException.forId(id)));
    }

    //No service-wide mapping of DuplicateKeyException exists (the cholewa-commons default would
    //answer a bare "Duplicate Key") - the violated index tells which binding was taken, and the
    //row that was refused what it was taken with. An index nobody named here is not ours to word
    private static Throwable bindingTaken(final DuplicateKeyException exception, final DeviceEntity device) {
        final String message = String.valueOf(exception.getMessage());
        if (message.contains(SHELLY_ADDRESS_UNIQUE_INDEX)) {
            return new DeviceConflictException(
                "A device is already registered for the Shelly at [" + device.getShellyAddress() + "]"
                    + channelOf(device));
        }
        if (message.contains(SHELLY_DEVICE_ID_UNIQUE_INDEX)) {
            return new DeviceConflictException(
                "A device is already registered for the Shelly with the id [" + device.getShellyDeviceId() + "]"
                    + channelOf(device));
        }
        if (message.contains(EATON_POINT_UNIQUE_CONSTRAINT)) {
            return new DeviceConflictException(
                "A device is already registered for the point [" + device.getEatonPoint() + "] of the Eaton gateway ["
                    + device.getEatonGateway().getValue() + "]");
        }
        if (message.contains(BTHOME_ADDRESS_UNIQUE_CONSTRAINT)) {
            return new DeviceConflictException(
                "A device is already registered for the Bluetooth address [" + device.getBthomeAddress() + "]");
        }
        return exception;
    }

    //a Shelly without a channel is one device as a whole, and is taken as a whole
    private static String channelOf(final DeviceEntity device) {
        return device.getShellyChannel() == null ? "" : ", channel [" + device.getShellyChannel() + "]";
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
