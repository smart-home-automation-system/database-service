package cloud.cholewa.data.error;

import java.time.LocalDate;

//a day the household did not add to the calendar - which a holiday or a weekend is as well: they
//are days off by rule, and no row stands for them
public class DayOffNotFoundException extends RuntimeException {

    private DayOffNotFoundException(final String message) {
        super(message);
    }

    public static DayOffNotFoundException forDate(final LocalDate date) {
        return new DayOffNotFoundException("No day off of the household is stored for: " + date);
    }
}
