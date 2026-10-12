package cloud.cholewa.data.calendar.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

//What a client says of a day off it sets: why. The day is in the path. No model of the SDK - the
//SDK has the day as the calendar answers it (CalendarDay), with a type a client does not choose.
//The bound is the one of CalendarDay.reason, and of the column (V15). No message on the constraints:
//cholewa-commons answers a refused body by the name of its field alone
public record DayOffRequest(
    @NotBlank
    @Size(max = DayOffRequest.MAX_REASON_LENGTH)
    String reason
) {

    static final int MAX_REASON_LENGTH = 100;
}
