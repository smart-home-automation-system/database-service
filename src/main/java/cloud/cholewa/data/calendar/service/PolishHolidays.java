package cloud.cholewa.data.calendar.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.Month;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

//The statutory holidays of Poland - the days off work by law, computed, not stored: nine or ten
//fixed dates and four that move with Easter. Two of the fixed ones have not been holidays for the
//whole range: Epiphany since 2011, Christmas Eve since 2025.
//The range is 2000 to 2099, the years the calendar of the house answers for. The Easter rule
//itself holds for any year of the Gregorian calendar; the list of holidays is what would not.
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PolishHolidays {

    public static final int FIRST_YEAR = 2000;
    public static final int LAST_YEAR = 2099;

    private static final int EPIPHANY_SINCE = 2011;
    private static final int CHRISTMAS_EVE_SINCE = 2025;

    //Pentecost is the seventh Sunday after Easter, Corpus Christi the Thursday after Trinity Sunday
    private static final int DAYS_TO_PENTECOST = 49;
    private static final int DAYS_TO_CORPUS_CHRISTI = 60;

    //the holidays of a year by their date, in the order of the calendar
    public static Map<LocalDate, String> of(final int year) {
        if (year < FIRST_YEAR || year > LAST_YEAR) {
            throw new IllegalArgumentException(
                "The holidays are known for the years " + FIRST_YEAR + " to " + LAST_YEAR + ", not for " + year);
        }

        final LocalDate easter = easterSunday(year);
        final Map<LocalDate, String> holidays = new TreeMap<>();

        holidays.put(LocalDate.of(year, Month.JANUARY, 1), "New Year's Day");
        if (year >= EPIPHANY_SINCE) {
            holidays.put(LocalDate.of(year, Month.JANUARY, 6), "Epiphany");
        }
        holidays.put(easter, "Easter Sunday");
        holidays.put(easter.plusDays(1), "Easter Monday");
        holidays.put(LocalDate.of(year, Month.MAY, 1), "Labour Day");
        holidays.put(LocalDate.of(year, Month.MAY, 3), "Constitution Day");
        holidays.put(easter.plusDays(DAYS_TO_PENTECOST), "Pentecost Sunday");
        holidays.put(easter.plusDays(DAYS_TO_CORPUS_CHRISTI), "Corpus Christi");
        holidays.put(LocalDate.of(year, Month.AUGUST, 15), "Assumption of Mary");
        holidays.put(LocalDate.of(year, Month.NOVEMBER, 1), "All Saints' Day");
        holidays.put(LocalDate.of(year, Month.NOVEMBER, 11), "Independence Day");
        if (year >= CHRISTMAS_EVE_SINCE) {
            holidays.put(LocalDate.of(year, Month.DECEMBER, 24), "Christmas Eve");
        }
        holidays.put(LocalDate.of(year, Month.DECEMBER, 25), "Christmas Day");
        holidays.put(LocalDate.of(year, Month.DECEMBER, 26), "Second Day of Christmas");

        return Collections.unmodifiableMap(holidays);
    }

    //The "anonymous Gregorian algorithm" (Meeus / Jones / Butcher): the Sunday after the first
    //full moon of spring as the church reckons it. Integer arithmetic only, no table of years
    static LocalDate easterSunday(final int year) {
        final int a = year % 19;
        final int b = year / 100;
        final int c = year % 100;
        final int d = b / 4;
        final int e = b % 4;
        final int f = (b + 8) / 25;
        final int g = (b - f + 1) / 3;
        final int h = (19 * a + b - d - g + 15) % 30;
        final int i = c / 4;
        final int k = c % 4;
        final int l = (32 + 2 * e + 2 * i - h - k) % 7;
        final int m = (a + 11 * h + 22 * l) / 451;
        final int month = (h + l - 7 * m + 114) / 31;
        final int day = (h + l - 7 * m + 114) % 31 + 1;

        return LocalDate.of(year, month, day);
    }
}
