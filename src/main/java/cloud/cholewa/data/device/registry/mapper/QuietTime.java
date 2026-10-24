package cloud.cholewa.data.device.registry.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

//The quiet time of an appliance between the two forms it has: HH:mm text in the SDK model (OpenAPI
//has no local time) and a TIME column here. The model's pattern lets through only what parses, so
//a text that does not is a body that skipped validation - and fails here rather than being stored
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QuietTime {

    private static final DateTimeFormatter HOURS_AND_MINUTES = DateTimeFormatter.ofPattern("HH:mm");

    static LocalTime parse(final String text) {
        return text == null ? null : LocalTime.parse(text, HOURS_AND_MINUTES);
    }

    static String format(final LocalTime time) {
        return time == null ? null : time.format(HOURS_AND_MINUTES);
    }
}
