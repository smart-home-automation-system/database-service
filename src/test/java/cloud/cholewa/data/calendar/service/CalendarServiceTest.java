package cloud.cholewa.data.calendar.service;

import cloud.cholewa.data.calendar.model.DayOffEntity;
import cloud.cholewa.data.calendar.repository.DayOffRepository;
import cloud.cholewa.data.error.DayOffNotFoundException;
import cloud.cholewa.home.model.CalendarDay;
import cloud.cholewa.home.model.DayType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock
    private DayOffRepository repository;

    @InjectMocks
    private CalendarService sut;

    //Thursday 30 April to Tuesday 5 May 2026: a workday, Labour Day on a Friday, a Saturday,
    //Constitution Day on a Sunday - the holiday is named, not the day of the week - and two
    //workdays. The end of the range is not answered
    @Test
    void should_tell_the_workdays_from_the_holidays_and_the_weekends_of_a_range() {
        final LocalDate from = LocalDate.of(2026, 4, 30);
        final LocalDate to = LocalDate.of(2026, 5, 6);
        when(repository.findAllBetween(from, to)).thenReturn(Flux.empty());

        sut.days(from, to).as(StepVerifier::create)
            .assertNext(days -> assertThat(days).containsExactly(
                workday(2026, 4, 30),
                dayOff(2026, 5, 1, "Labour Day"),
                dayOff(2026, 5, 2, "Saturday"),
                dayOff(2026, 5, 3, "Constitution Day"),
                workday(2026, 5, 4),
                workday(2026, 5, 5)
            ))
            .verifyComplete();
    }

    //The word of the household comes first: a workday it set is a day off, and a holiday or a
    //Saturday it set reads with its reason instead of the one the rules would give
    @Test
    void should_answer_a_stored_day_as_a_day_off_with_the_reason_of_the_household() {
        final LocalDate from = LocalDate.of(2026, 4, 30);
        final LocalDate to = LocalDate.of(2026, 5, 5);
        when(repository.findAllBetween(from, to)).thenReturn(Flux.just(
            stored(2026, 5, 4, "Long weekend"),
            stored(2026, 5, 1, "Barbecue"),
            stored(2026, 5, 2, "Trip")
        ));

        sut.days(from, to).as(StepVerifier::create)
            .assertNext(days -> assertThat(days).containsExactly(
                workday(2026, 4, 30),
                dayOff(2026, 5, 1, "Barbecue"),
                dayOff(2026, 5, 2, "Trip"),
                dayOff(2026, 5, 3, "Constitution Day"),
                dayOff(2026, 5, 4, "Long weekend")
            ))
            .verifyComplete();
    }

    //a range across the turn of a year needs the holidays of both
    @Test
    void should_know_the_holidays_of_every_year_a_range_touches() {
        final LocalDate from = LocalDate.of(2026, 12, 31);
        final LocalDate to = LocalDate.of(2027, 1, 2);
        when(repository.findAllBetween(from, to)).thenReturn(Flux.empty());

        sut.days(from, to).as(StepVerifier::create)
            .assertNext(days -> assertThat(days).containsExactly(
                workday(2026, 12, 31),
                dayOff(2027, 1, 1, "New Year's Day")
            ))
            .verifyComplete();
    }

    //the two ends of what one call may ask for: two whole years, and the whole last year of the
    //calendar - whose end, 1 January 2100, is named and never answered
    @Test
    void should_answer_the_longest_range_and_the_last_year_of_the_calendar() {
        when(repository.findAllBetween(any(), any())).thenReturn(Flux.empty());

        sut.days(LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1)).as(StepVerifier::create)
            .assertNext(days -> assertThat(days).hasSize(730))
            .verifyComplete();

        sut.days(LocalDate.of(2099, 1, 1), LocalDate.of(2100, 1, 1)).as(StepVerifier::create)
            .assertNext(days -> assertThat(days).hasSize(365)
                .last().isEqualTo(workday(2099, 12, 31)))
            .verifyComplete();
    }

    //a range the service cannot serve is a bad request, and no query runs for it
    @ParameterizedTest
    @CsvSource({
        "2026-05-01, 2026-05-01, has to start before it ends",
        "2026-05-02, 2026-05-01, has to start before it ends",
        "2026-01-01, 2028-01-02, longer than 2 years",
        "1999-12-31, 2000-01-02, covers the years 2000 to 2099",
        "2099-12-31, 2100-01-02, covers the years 2000 to 2099"
    })
    void should_refuse_a_range_it_cannot_serve(final LocalDate from, final LocalDate to, final String reason) {
        sut.days(from, to).as(StepVerifier::create)
            .expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(ServerWebInputException.class)
                .hasMessageContaining(reason)
                .hasMessageContaining(from.toString())
                .hasMessageContaining(to.toString()))
            .verify();

        verifyNoInteractions(repository);
    }

    @Test
    void should_store_a_day_off_and_answer_it_as_the_calendar_reads_it() {
        final LocalDate date = LocalDate.of(2026, 5, 4);
        when(repository.set(eq(date), eq("Long weekend"), any(LocalDateTime.class))).thenReturn(Mono.empty());

        sut.setDayOff(date, "Long weekend").as(StepVerifier::create)
            .expectNext(dayOff(2026, 5, 4, "Long weekend"))
            .verifyComplete();

        verify(repository).set(eq(date), eq("Long weekend"), any(LocalDateTime.class));
    }

    //a day the calendar never answers for would be stored and never read
    @ParameterizedTest
    @CsvSource({"1999-12-31", "2100-01-01"})
    void should_refuse_a_day_off_outside_the_years_of_the_calendar(final LocalDate date) {
        sut.setDayOff(date, "Long weekend").as(StepVerifier::create)
            .expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(ServerWebInputException.class)
                .hasMessageContaining("covers the years 2000 to 2099")
                .hasMessageContaining(date.toString()))
            .verify();

        verifyNoInteractions(repository);
    }

    @Test
    void should_remove_a_stored_day_off() {
        final LocalDate date = LocalDate.of(2026, 5, 4);
        when(repository.deleteByDate(date)).thenReturn(Mono.just(1L));

        sut.removeDayOff(date).as(StepVerifier::create).verifyComplete();
    }

    //1 May is a day off and no row: there is nothing of the household to take back
    @Test
    void should_fail_when_the_day_to_remove_is_not_stored() {
        final LocalDate date = LocalDate.of(2026, 5, 1);
        when(repository.deleteByDate(date)).thenReturn(Mono.just(0L));

        sut.removeDayOff(date).as(StepVerifier::create)
            .expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(DayOffNotFoundException.class)
                .hasMessage("No day off of the household is stored for: 2026-05-01"))
            .verify();
    }

    private static DayOffEntity stored(final int year, final int month, final int day, final String reason) {
        return DayOffEntity.builder()
            .date(LocalDate.of(year, month, day))
            .reason(reason)
            .createdAt(LocalDateTime.of(2026, 1, 1, 12, 0))
            .build();
    }

    private static CalendarDay workday(final int year, final int month, final int day) {
        return CalendarDay.builder().date(LocalDate.of(year, month, day)).type(DayType.WORKDAY).build();
    }

    private static CalendarDay dayOff(final int year, final int month, final int day, final String reason) {
        return CalendarDay.builder().date(LocalDate.of(year, month, day)).type(DayType.DAY_OFF).reason(reason).build();
    }
}
