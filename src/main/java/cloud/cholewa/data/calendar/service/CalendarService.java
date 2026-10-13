package cloud.cholewa.data.calendar.service;

import cloud.cholewa.data.calendar.model.DayOffEntity;
import cloud.cholewa.data.calendar.repository.DayOffRepository;
import cloud.cholewa.data.error.DayOffNotFoundException;
import cloud.cholewa.home.model.CalendarDay;
import cloud.cholewa.home.model.DayType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//The calendar of the house: which days the household works. A day is a day off for one of three
//reasons - the household said so (a row of days_off), the law says so (PolishHolidays), or it is a
//Saturday or a Sunday - and a workday otherwise. Only the first of them is stored.
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarService {

    //the longest range one call answers: 731 days at most, a list a client can hold
    private static final int MAX_YEARS = 2;

    //the first day the calendar does not answer for - the holidays are known up to the year before
    private static final LocalDate END_OF_CALENDAR = LocalDate.of(PolishHolidays.LAST_YEAR + 1, 1, 1);
    private static final LocalDate START_OF_CALENDAR = LocalDate.of(PolishHolidays.FIRST_YEAR, 1, 1);

    private final DayOffRepository repository;

    //Every day of the range, the start included and the end not, in the order of the calendar.
    //The range is checked first: a refused one runs no query
    public Mono<List<CalendarDay>> days(final LocalDate from, final LocalDate to) {
        return Mono.fromRunnable(() -> checkRange(from, to))
            .thenMany(Flux.defer(() -> repository.findAllBetween(from, to)))
            .collectMap(DayOffEntity::getDate, DayOffEntity::getReason)
            .map(stored -> {
                final Map<LocalDate, String> holidays = holidaysOf(from, to);
                return from.datesUntil(to).map(date -> day(date, stored, holidays)).toList();
            });
    }

    //Idempotent: a day set twice is stored once, with the reason of the second call. A weekend
    //or a holiday may be set as well - it is a day off either way, and from then on reads with
    //the reason of the household. The reason is stored without the spaces around it
    public Mono<CalendarDay> setDayOff(final LocalDate date, final String reason) {
        final String stored = reason.strip();
        return Mono.fromRunnable(() -> checkWithinCalendar(date))
            .then(Mono.defer(() -> repository.set(date, stored, LocalDateTime.now(ZoneId.systemDefault()))))
            .then(Mono.fromSupplier(() -> dayOff(date, stored)))
            .doOnNext(day -> log.info("Day off set: {} [{}]", date, stored));
    }

    //Only what the household added can be taken back. A holiday or a weekend is not stored, so
    //removing one is "not found" - and the day stays a day off
    public Mono<Void> removeDayOff(final LocalDate date) {
        return repository.deleteByDate(date)
            .filter(removed -> removed > 0)
            .switchIfEmpty(Mono.error(() -> DayOffNotFoundException.forDate(date)))
            .doOnNext(removed -> log.info("Day off removed: {}", date))
            .then();
    }

    //The word of the household first: a day it set carries the reason it gave, also when the day
    //is a holiday or a weekend anyway. Then the holiday by its name, then the weekend by its day
    private static CalendarDay day(
        final LocalDate date,
        final Map<LocalDate, String> stored,
        final Map<LocalDate, String> holidays
    ) {
        final String reason = stored.getOrDefault(date, holidays.get(date));
        if (reason != null) {
            return dayOff(date, reason);
        }
        return switch (date.getDayOfWeek()) {
            case SATURDAY -> dayOff(date, "Saturday");
            case SUNDAY -> dayOff(date, "Sunday");
            default -> CalendarDay.builder().date(date).type(DayType.WORKDAY).build();
        };
    }

    private static CalendarDay dayOff(final LocalDate date, final String reason) {
        return CalendarDay.builder().date(date).type(DayType.DAY_OFF).reason(reason).build();
    }

    //the holidays of every year the range touches - three years at most
    private static Map<LocalDate, String> holidaysOf(final LocalDate from, final LocalDate to) {
        final Map<LocalDate, String> holidays = new HashMap<>();
        for (int year = from.getYear(); year <= to.minusDays(1).getYear(); year++) {
            holidays.putAll(PolishHolidays.of(year));
        }
        return holidays;
    }

    //A bad request, answered like a parameter of the wrong type - 400 without a code, the way
    //the reports of the other services answer a range they cannot serve
    private static void checkRange(final LocalDate from, final LocalDate to) {
        if (!from.isBefore(to)) {
            throw new ServerWebInputException(
                "The range has to start before it ends: from [" + from + "] to [" + to + "]");
        }
        //before anything is added to a date: a year of nine digits is a date too, and two years
        //more than the last of them is not
        if (from.isBefore(START_OF_CALENDAR) || to.isAfter(END_OF_CALENDAR)) {
            throw new ServerWebInputException(outsideTheCalendar("from [" + from + "] to [" + to + "]"));
        }
        if (to.isAfter(from.plusYears(MAX_YEARS))) {
            throw new ServerWebInputException(
                "The range is longer than " + MAX_YEARS + " years: from [" + from + "] to [" + to + "]");
        }
    }

    //a day nobody could ever read back is not stored
    private static void checkWithinCalendar(final LocalDate date) {
        if (date.isBefore(START_OF_CALENDAR) || !date.isBefore(END_OF_CALENDAR)) {
            throw new ServerWebInputException(outsideTheCalendar("[" + date + "]"));
        }
    }

    private static String outsideTheCalendar(final String what) {
        return "The calendar covers the years " + PolishHolidays.FIRST_YEAR + " to " + PolishHolidays.LAST_YEAR
            + ": " + what;
    }
}
