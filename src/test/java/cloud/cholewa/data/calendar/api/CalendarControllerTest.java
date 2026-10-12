package cloud.cholewa.data.calendar.api;

import cloud.cholewa.data.calendar.service.CalendarService;
import cloud.cholewa.data.config.ExceptionHandlerConfig;
import cloud.cholewa.data.error.DayOffNotFoundException;
import cloud.cholewa.home.model.CalendarDay;
import cloud.cholewa.home.model.DayType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Import(ExceptionHandlerConfig.class)
@WebFluxTest(controllers = CalendarController.class)
class CalendarControllerTest {

    private static final LocalDate LABOUR_DAY = LocalDate.of(2026, 5, 1);

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CalendarService calendarService;

    //The JSON a reader gets, written out: the date as ISO text, the type by the value of the SDK
    //("day off", not DAY_OFF), and no reason at all on a workday
    @Test
    void should_return_the_days_as_the_sdk_writes_them() {
        when(calendarService.days(LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 2))).thenReturn(Mono.just(List.of(
            CalendarDay.builder().date(LocalDate.of(2026, 4, 30)).type(DayType.WORKDAY).build(),
            CalendarDay.builder().date(LABOUR_DAY).type(DayType.DAY_OFF).reason("Labour Day").build()
        )));

        webTestClient.get()
            .uri("/calendar/days?from=2026-04-30&to=2026-05-02")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("""
                [
                  {"date": "2026-04-30", "type": "workday"},
                  {"date": "2026-05-01", "type": "day off", "reason": "Labour Day"}
                ]""", JsonCompareMode.STRICT);
    }

    //a date in the form of a locale, a date that does not exist, no date at all - a bad request
    //before the service is asked
    @ParameterizedTest
    @ValueSource(strings = {
        "/calendar/days?from=30.04.2026&to=2026-05-02",
        "/calendar/days?from=2026-04-30&to=5/2/26",
        "/calendar/days?from=2026-02-30&to=2026-05-02",
        "/calendar/days?from=2026-04-30",
        "/calendar/days"
    })
    void should_answer_bad_request_for_a_range_it_cannot_read(final String uri) {
        webTestClient.get()
            .uri(uri)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0].code").doesNotExist();

        verifyNoInteractions(calendarService);
    }

    //what the service answers a range it cannot serve with: 400, no code - not a named cause
    @Test
    void should_answer_bad_request_for_a_range_the_service_refuses() {
        when(calendarService.days(LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1)))
            .thenReturn(Mono.error(new ServerWebInputException("The range has to start before it ends")));

        webTestClient.get()
            .uri("/calendar/days?from=2026-05-02&to=2026-05-01")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0].code").doesNotExist();
    }

    @Test
    void should_set_a_day_off_and_answer_the_day() {
        when(calendarService.setDayOff(LocalDate.of(2026, 5, 4), "Long weekend")).thenReturn(Mono.just(
            CalendarDay.builder().date(LocalDate.of(2026, 5, 4)).type(DayType.DAY_OFF).reason("Long weekend").build()
        ));

        webTestClient.put()
            .uri("/calendar/days-off/2026-05-04")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"reason": "Long weekend"}""")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("""
                {"date": "2026-05-04", "type": "day off", "reason": "Long weekend"}""", JsonCompareMode.STRICT);
    }

    //the reason is what the household wrote, and it has to have written something; the answer
    //names the field, as cholewa-commons answers every refused body
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reason\": null}", "{\"reason\": \"\"}", "{\"reason\": \"   \"}"})
    void should_refuse_a_day_off_without_a_reason(final String body) {
        webTestClient.put()
            .uri("/calendar/days-off/2026-05-04")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0].details").isEqualTo("reason");

        verifyNoInteractions(calendarService);
    }

    @Test
    void should_refuse_a_reason_longer_than_the_model_holds() {
        webTestClient.put()
            .uri("/calendar/days-off/2026-05-04")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"reason\": \"" + "x".repeat(101) + "\"}")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0].details").isEqualTo("reason");

        verifyNoInteractions(calendarService);
    }

    @Test
    void should_accept_a_reason_of_exactly_the_length_the_model_holds() {
        final String reason = "x".repeat(100);
        when(calendarService.setDayOff(LocalDate.of(2026, 5, 4), reason)).thenReturn(Mono.just(
            CalendarDay.builder().date(LocalDate.of(2026, 5, 4)).type(DayType.DAY_OFF).reason(reason).build()
        ));

        webTestClient.put()
            .uri("/calendar/days-off/2026-05-04")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"reason\": \"" + reason + "\"}")
            .exchange()
            .expectStatus().isOk();
    }

    @ParameterizedTest
    @ValueSource(strings = {"04.05.2026", "2026-13-01", "tomorrow"})
    void should_answer_bad_request_for_a_day_it_cannot_read(final String date) {
        webTestClient.put()
            .uri("/calendar/days-off/{date}", date)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {"reason": "Long weekend"}""")
            .exchange()
            .expectStatus().isBadRequest();

        webTestClient.delete()
            .uri("/calendar/days-off/{date}", date)
            .exchange()
            .expectStatus().isBadRequest();

        verifyNoInteractions(calendarService);
    }

    @Test
    void should_remove_a_day_off() {
        when(calendarService.removeDayOff(LocalDate.of(2026, 5, 4))).thenReturn(Mono.empty());

        webTestClient.delete()
            .uri("/calendar/days-off/2026-05-04")
            .exchange()
            .expectStatus().isNoContent()
            .expectBody().isEmpty();

        verify(calendarService).removeDayOff(LocalDate.of(2026, 5, 4));
    }

    //the code a caller tells "this day was not set by the household" from a wrong path by
    @Test
    void should_answer_not_found_with_its_code_for_a_day_that_is_not_stored() {
        when(calendarService.removeDayOff(LABOUR_DAY)).thenReturn(Mono.error(DayOffNotFoundException.forDate(LABOUR_DAY)));

        webTestClient.delete()
            .uri("/calendar/days-off/2026-05-01")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_DAY_OFF")
            .jsonPath("$.errors[0].message").isEqualTo("Day off not found")
            .jsonPath("$.errors[0].details").isEqualTo("No day off of the household is stored for: 2026-05-01");
    }
}
