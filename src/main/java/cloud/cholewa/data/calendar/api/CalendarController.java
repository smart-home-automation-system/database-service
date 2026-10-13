package cloud.cholewa.data.calendar.api;

import cloud.cholewa.data.calendar.service.CalendarService;
import cloud.cholewa.home.model.CalendarDay;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

//Every date is written as 2026-05-01 and said so on the parameter: left to the default, Spring
//reads a date in the short form of the locale of the JVM. By a pattern and not by ISO.DATE,
//which also reads a date with an offset (2026-05-04+02:00) and drops the offset without a word.
//Spring reads the pattern strictly: 30 February is no date
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("calendar")
public class CalendarController {

    private static final String DATE = "yyyy-MM-dd";

    private final CalendarService calendarService;

    //every day of the range, the start included and the end not
    @GetMapping("days")
    Mono<ResponseEntity<List<CalendarDay>>> getDays(
        @RequestParam @DateTimeFormat(pattern = DATE) final LocalDate from,
        @RequestParam @DateTimeFormat(pattern = DATE) final LocalDate to
    ) {
        return calendarService.days(from, to)
            .doOnSubscribe(subscription -> log.info("Querying for the days of the calendar from: {} to: {}", from, to))
            .map(ResponseEntity::ok);
    }

    //PUT on the day itself: set twice it is stored once, with the reason of the second call
    @PutMapping("days-off/{date}")
    Mono<ResponseEntity<CalendarDay>> setDayOff(
        @PathVariable @DateTimeFormat(pattern = DATE) final LocalDate date,
        @Valid @RequestBody final DayOffRequest request
    ) {
        return calendarService.setDayOff(date, request.reason())
            .map(ResponseEntity::ok);
    }

    @DeleteMapping("days-off/{date}")
    Mono<ResponseEntity<Void>> removeDayOff(@PathVariable @DateTimeFormat(pattern = DATE) final LocalDate date) {
        return calendarService.removeDayOff(date)
            .then(Mono.just(ResponseEntity.noContent().build()));
    }
}
