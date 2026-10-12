package cloud.cholewa.data.calendar.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//The holidays are computed, so the years the house will live through first are written out by hand
//from the calendar: a slip in the Easter rule, or in the distance of a holiday from Easter, shows
//here as a date and not as a blinds schedule that ran on the wrong morning.
class PolishHolidaysTest {

    //Easter Sunday, Pentecost and Corpus Christi of 2026 to 2030, as the calendars print them
    @ParameterizedTest
    @CsvSource({
        "2026, 2026-04-05, 2026-05-24, 2026-06-04",
        "2027, 2027-03-28, 2027-05-16, 2027-05-27",
        "2028, 2028-04-16, 2028-06-04, 2028-06-15",
        "2029, 2029-04-01, 2029-05-20, 2029-05-31",
        "2030, 2030-04-21, 2030-06-09, 2030-06-20"
    })
    void should_place_the_movable_holidays_of_the_coming_years(
        final int year,
        final LocalDate easter,
        final LocalDate pentecost,
        final LocalDate corpusChristi
    ) {
        assertThat(PolishHolidays.of(year))
            .containsEntry(easter, "Easter Sunday")
            .containsEntry(easter.plusDays(1), "Easter Monday")
            .containsEntry(pentecost, "Pentecost Sunday")
            .containsEntry(corpusChristi, "Corpus Christi");
    }

    //the whole list of one year, in the order of the calendar - nothing more, nothing less
    @Test
    void should_list_every_holiday_of_a_year_with_its_name() {
        assertThat(PolishHolidays.of(2026)).containsExactly(
            Map.entry(LocalDate.of(2026, 1, 1), "New Year's Day"),
            Map.entry(LocalDate.of(2026, 1, 6), "Epiphany"),
            Map.entry(LocalDate.of(2026, 4, 5), "Easter Sunday"),
            Map.entry(LocalDate.of(2026, 4, 6), "Easter Monday"),
            Map.entry(LocalDate.of(2026, 5, 1), "Labour Day"),
            Map.entry(LocalDate.of(2026, 5, 3), "Constitution Day"),
            Map.entry(LocalDate.of(2026, 5, 24), "Pentecost Sunday"),
            Map.entry(LocalDate.of(2026, 6, 4), "Corpus Christi"),
            Map.entry(LocalDate.of(2026, 8, 15), "Assumption of Mary"),
            Map.entry(LocalDate.of(2026, 11, 1), "All Saints' Day"),
            Map.entry(LocalDate.of(2026, 11, 11), "Independence Day"),
            Map.entry(LocalDate.of(2026, 12, 24), "Christmas Eve"),
            Map.entry(LocalDate.of(2026, 12, 25), "Christmas Day"),
            Map.entry(LocalDate.of(2026, 12, 26), "Second Day of Christmas")
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {2027, 2028, 2029, 2030})
    void should_have_fourteen_holidays_in_each_of_the_coming_years(final int year) {
        assertThat(PolishHolidays.of(year)).hasSize(14);
    }

    //a holiday since 2025 - the year before, the day was a workday
    @Test
    void should_count_christmas_eve_from_2025_on() {
        assertThat(PolishHolidays.of(2024)).doesNotContainKey(LocalDate.of(2024, 12, 24));
        assertThat(PolishHolidays.of(2025)).containsEntry(LocalDate.of(2025, 12, 24), "Christmas Eve");
    }

    @Test
    void should_count_epiphany_from_2011_on() {
        assertThat(PolishHolidays.of(2010)).doesNotContainKey(LocalDate.of(2010, 1, 6));
        assertThat(PolishHolidays.of(2011)).containsEntry(LocalDate.of(2011, 1, 6), "Epiphany");
    }

    //What holds for every year, whatever the rule computed: Easter is a Sunday between 22 March
    //and 25 April, Corpus Christi a Thursday. The two ends of the range are 2008 and 2038 (23
    //March, 25 April) - the earliest and the latest Easter of the century
    @Test
    void should_keep_easter_on_a_sunday_of_spring_in_every_year_of_the_range() {
        IntStream.rangeClosed(PolishHolidays.FIRST_YEAR, PolishHolidays.LAST_YEAR).forEach(year -> {
            final LocalDate easter = PolishHolidays.easterSunday(year);

            assertThat(easter.getDayOfWeek()).as("Easter of %s", year).isEqualTo(DayOfWeek.SUNDAY);
            assertThat(easter).as("Easter of %s", year)
                .isBetween(LocalDate.of(year, 3, 22), LocalDate.of(year, 4, 25));
            assertThat(easter.plusDays(60).getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
        });

        assertThat(PolishHolidays.easterSunday(2008)).isEqualTo(LocalDate.of(2008, 3, 23));
        assertThat(PolishHolidays.easterSunday(2038)).isEqualTo(LocalDate.of(2038, 4, 25));
    }

    //no two holidays of a year fall on one day - the map would keep one of them, silently
    @Test
    void should_never_put_two_holidays_on_one_day() {
        IntStream.rangeClosed(PolishHolidays.FIRST_YEAR, PolishHolidays.LAST_YEAR).forEach(year -> {
            final int expected = 12 + (year >= 2011 ? 1 : 0) + (year >= 2025 ? 1 : 0);

            assertThat(PolishHolidays.of(year)).as("holidays of %s", year).hasSize(expected);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1999, 2100})
    void should_refuse_a_year_outside_the_range(final int year) {
        assertThatThrownBy(() -> PolishHolidays.of(year))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("2000 to 2099")
            .hasMessageContaining(String.valueOf(year));
    }
}
