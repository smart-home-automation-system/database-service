package cloud.cholewa.data.device.registry.service;

import cloud.cholewa.data.error.InvalidDeviceException;
import cloud.cholewa.home.model.ApplianceSettings;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.SmartDevice;
import cloud.cholewa.home.model.SmartVendor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

//What the SDK model leaves open: its three bindings and the three fields of one kind only are all
//optional, and nothing in it ties them to the vendor and the kind of the device. The registry
//does, here, before a device is stored - the schema (V13) repeats what one row can say of it, and
//would answer with the name of a constraint where this answers in words.
//Everything is reported by the value a client sends ("weather station"), not by a constant.
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DeviceRules {

    private static final int MIN_NAME_CHARACTERS = 3;
    private static final int FIRST_GENERATION_ON_MQTT = 2;
    private static final int LAST_OUTPUT = 3;
    private static final int FIRST_ZONE = 200;

    static void check(final SmartDevice device) {
        final Binding binding = bindingOf(device);
        final SmartVendor vendor = device.getVendor();
        final DeviceKind kind = device.getKind();

        //an Eaton device is reached through a gateway of the AMX system and nothing else is; the
        //binding of a Shelly is a Shelly's. A Bluetooth sensor is of any vendor but Eaton
        if ((vendor == SmartVendor.EATON) != (binding == Binding.EATON)
            || (binding == Binding.SHELLY && vendor != SmartVendor.SHELLY)) {
            throw new InvalidDeviceException(
                "A device of the vendor [" + vendor.getValue() + "] is not reached through the binding [" + binding + "]");
        }

        final Set<Binding> possible = bindingsOf(kind);
        if (!possible.contains(binding)) {
            throw new InvalidDeviceException(
                "A device of the kind [" + kind.getValue() + "] is not reached through the binding [" + binding
                    + "], only through: " + possible.stream().map(Binding::toString).collect(Collectors.joining(", ")));
        }

        //A Shelly from the second generation on reports over MQTT, and the services find the
        //device of an event by the id it reports under. Without the id the device can be driven
        //and never heard: its events are dropped as nobody's, with no error anywhere - and the
        //unique index on the id does not count the row. A Gen1 device has no such id to give
        if (binding == Binding.SHELLY
            && device.getShelly().getGeneration() >= FIRST_GENERATION_ON_MQTT
            && device.getShelly().getDeviceId() == null) {
            throw new InvalidDeviceException(
                "A Shelly of generation " + device.getShelly().getGeneration()
                    + " needs its deviceId - the id it reports under over MQTT");
        }

        checkChannel(device, binding);

        //The model counts the units of a string, the schema its characters (devices_name_length):
        //a name of one letter and one emoji is three to the first and two to the second
        if (device.getName().codePointCount(0, device.getName().length()) < MIN_NAME_CHARACTERS) {
            throw new InvalidDeviceException("The name of a device has at least " + MIN_NAME_CHARACTERS + " characters");
        }

        if (device.getLightKind() != null && kind != DeviceKind.LIGHT) {
            throw new InvalidDeviceException(
                "lightKind describes a light, not a device of the kind [" + kind.getValue() + "]");
        }
        if ((device.getPlacement() != null || device.getPositioning() != null) && kind != DeviceKind.BLINDS) {
            throw new InvalidDeviceException(
                "placement and positioning describe blinds, not a device of the kind [" + kind.getValue() + "]");
        }

        checkOpeningSensors(device);
        checkAppliance(device);
    }

    //The settings of an appliance (HAS-272): there for the kind appliance and for nothing else - an
    //appliance without them could not tell a cycle from the draw of its plug. The model checks each
    //setting on its own (@Valid); the one rule between two of them is here, and the schema repeats
    //both (V18)
    private static void checkAppliance(final SmartDevice device) {
        final ApplianceSettings appliance = device.getAppliance();
        final boolean isAppliance = device.getKind() == DeviceKind.APPLIANCE;
        if (isAppliance && appliance == null) {
            throw new InvalidDeviceException("A device of the kind [appliance] needs its appliance settings");
        }
        if (!isAppliance && appliance != null) {
            throw new InvalidDeviceException(
                "appliance describes an appliance, not a device of the kind [" + device.getKind().getValue() + "]");
        }
        //both are required by the model; a body that skipped @Valid must not fail here with an NPE
        if (isAppliance && appliance.getIdleBelowWatts() != null && appliance.getRunningAboveWatts() != null
            && appliance.getIdleBelowWatts() >= appliance.getRunningAboveWatts()) {
            throw new InvalidDeviceException(
                "idleBelowWatts (" + appliance.getIdleBelowWatts() + ") has to be below runningAboveWatts ("
                    + appliance.getRunningAboveWatts() + ")");
        }
    }

    //The channel of a Shelly (HAS-260): 0 to 3 is an output, of any kind; 200 to 299 a dynamic
    //component - the zone of a presence sensor (presencezone:200), and nothing else has one. The
    //model takes 0 to 299, the schema repeats this rule (V17)
    private static void checkChannel(final SmartDevice device, final Binding binding) {
        final Integer channel = binding == Binding.SHELLY ? device.getShelly().getChannel() : null;
        if (channel == null || channel <= LAST_OUTPUT) {
            return;
        }
        if (channel < FIRST_ZONE) {
            throw new InvalidDeviceException(
                "The channel of a Shelly is an output (0 to 3) or the zone of a presence sensor (200 to 299), not " + channel);
        }
        if (device.getKind() != DeviceKind.PRESENCE_SENSOR) {
            throw new InvalidDeviceException(
                "The channel " + channel + " is a zone, which only a device of the kind [presence sensor] has, not one of the kind ["
                    + device.getKind().getValue() + "]");
        }
    }

    //The sensors on the window of blinds (HAS-290): blinds only, each id once and none missing -
    //the model lets a null item through (@Min says nothing of a null). That each id is an opening
    //sensor of the registry needs the database, and is the service's to ask
    private static void checkOpeningSensors(final SmartDevice device) {
        final List<Long> sensors = device.getOpeningSensors();
        if (sensors == null || sensors.isEmpty()) {
            return;
        }
        if (device.getKind() != DeviceKind.BLINDS) {
            throw new InvalidDeviceException(
                "openingSensors describe the window of blinds, not a device of the kind [" + device.getKind().getValue() + "]");
        }
        //List.of() answers contains(null) with an exception, not with false
        if (sensors.stream().anyMatch(Objects::isNull)) {
            throw new InvalidDeviceException("openingSensors hold the ids of devices, and one of them is null");
        }
        if (new HashSet<>(sensors).size() != sensors.size()) {
            throw new InvalidDeviceException("openingSensors name a sensor more than once: " + sensors);
        }
    }

    private static Binding bindingOf(final SmartDevice device) {
        final List<Binding> given = new ArrayList<>();
        if (device.getShelly() != null) {
            given.add(Binding.SHELLY);
        }
        if (device.getEaton() != null) {
            given.add(Binding.EATON);
        }
        if (device.getBthome() != null) {
            given.add(Binding.BTHOME);
        }
        if (given.size() != 1) {
            throw new InvalidDeviceException(
                "A device is reached through exactly one binding - shelly, eaton or bthome - and this one has "
                    + (given.isEmpty() ? "none" : given.stream().map(Binding::toString).collect(Collectors.joining(" and "))));
        }
        return given.getFirst();
    }

    //Every kind named: a kind added to the SDK has to be decided here, or the compiler stops.
    //What moves and what lights is wired - a relay of a Shelly or an actor of the AMX system - and
    //so is a push button: a wall transmitter of the AMX system, or an input of a Shelly. A
    //sensor is whatever reports it: the room sensors of the AMX system, a Shelly on the network,
    //a Bluetooth one. The weather station is read through a Bluetooth gateway, and such a gateway
    //is itself a Shelly on the network
    private static Set<Binding> bindingsOf(final DeviceKind kind) {
        return switch (kind) {
            case BLINDS, LIGHT, PUSH_BUTTON -> EnumSet.of(Binding.SHELLY, Binding.EATON);
            case OPENING_SENSOR, MOTION_SENSOR, PRESENCE_SENSOR, TEMPERATURE_SENSOR -> EnumSet.allOf(Binding.class);
            case WEATHER_STATION -> EnumSet.of(Binding.BTHOME);
            case BTHOME_GATEWAY -> EnumSet.of(Binding.SHELLY);
            //an appliance is watched through the Shelly plug it runs on (HAS-272)
            case APPLIANCE -> EnumSet.of(Binding.SHELLY);
        };
    }

    //named as the fields of the model are
    private enum Binding {
        SHELLY, EATON, BTHOME;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
