package cloud.cholewa.data.device.registry.service;

import cloud.cholewa.data.error.InvalidDeviceException;
import cloud.cholewa.home.model.DeviceKind;
import cloud.cholewa.home.model.SmartDevice;
import cloud.cholewa.home.model.SmartVendor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
    //What moves and what lights is wired - a relay of a Shelly or an actor of the AMX system. A
    //sensor is whatever reports it: the room sensors of the AMX system, a Shelly on the network,
    //a Bluetooth one. The weather station is read through a Bluetooth gateway, and such a gateway
    //is itself a Shelly on the network
    private static Set<Binding> bindingsOf(final DeviceKind kind) {
        return switch (kind) {
            case BLINDS, LIGHT -> EnumSet.of(Binding.SHELLY, Binding.EATON);
            case OPENING_SENSOR, MOTION_SENSOR, PRESENCE_SENSOR, TEMPERATURE_SENSOR -> EnumSet.allOf(Binding.class);
            case WEATHER_STATION -> EnumSet.of(Binding.BTHOME);
            case BTHOME_GATEWAY -> EnumSet.of(Binding.SHELLY);
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
