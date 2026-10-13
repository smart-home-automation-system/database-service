package cloud.cholewa.data.postgres;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

//The calendar on a real PostgreSQL, through the API: the one statement that is PostgreSQL's own
//(INSERT ... ON CONFLICT, which sets a day twice and stores it once), the range of the read with
//its start included and its end not, and a column named "date" read and written as a date.
class CalendarOnPostgresTest extends OnPostgres {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void should_store_a_day_off_once_and_change_its_reason_when_it_is_set_again() {
        set("2026-05-04", "Long weekend");
        final Map<String, Object> first = row("SELECT date, reason, created_at FROM days_off");

        set("2026-05-04", "Painters in the house");

        assertThat(row("SELECT date, reason, created_at FROM days_off"))
            .containsEntry("date", LocalDate.of(2026, 5, 4))
            .containsEntry("reason", "Painters in the house")
            //the day was added once: the second call does not make it new
            .containsEntry("created_at", first.get("created_at"));
        assertThat(first)
            .containsEntry("reason", "Long weekend");
        assertThat(first.get("created_at")).isNotNull();
    }

    //Thursday 30 April to Tuesday 5 May 2026, with the Monday set by the household, and two rows
    //outside the range - the day before its start and the day that is its end
    @Test
    void should_answer_a_range_with_the_stored_days_between_its_start_and_its_end() {
        set("2026-04-29", "Before the range");
        set("2026-04-30", "First day of the range");
        set("2026-05-04", "Long weekend");
        set("2026-05-05", "The end of the range");

        webTestClient.get()
            .uri("/calendar/days?from=2026-04-30&to=2026-05-05")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("""
                [
                  {"date": "2026-04-30", "type": "day off", "reason": "First day of the range"},
                  {"date": "2026-05-01", "type": "day off", "reason": "Labour Day"},
                  {"date": "2026-05-02", "type": "day off", "reason": "Saturday"},
                  {"date": "2026-05-03", "type": "day off", "reason": "Constitution Day"},
                  {"date": "2026-05-04", "type": "day off", "reason": "Long weekend"}
                ]""", JsonCompareMode.STRICT);
    }

    @Test
    void should_answer_a_calendar_without_stored_days() {
        webTestClient.get()
            .uri("/calendar/days?from=2026-05-04&to=2026-05-06")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("""
                [
                  {"date": "2026-05-04", "type": "workday"},
                  {"date": "2026-05-05", "type": "workday"}
                ]""", JsonCompareMode.STRICT);
    }

    @Test
    void should_remove_a_day_off_and_leave_the_others() {
        set("2026-05-04", "Long weekend");
        set("2026-06-05", "After Corpus Christi");

        webTestClient.delete()
            .uri("/calendar/days-off/2026-05-04")
            .exchange()
            .expectStatus().isNoContent();

        assertThat(rows("SELECT date FROM days_off"))
            .extracting(day -> day.get("date"))
            .isEqualTo(List.of(LocalDate.of(2026, 6, 5)));

        //gone, so a second removal finds nothing - and neither does the removal of a holiday,
        //which is a day off without a row
        notFound("2026-05-04");
        notFound("2026-05-01");
    }

    //the bounds of the API and of the schema agree: what @Valid lets through, the table takes
    @Test
    void should_store_a_reason_of_the_greatest_length() {
        final String reason = "ż".repeat(100);

        set("2026-05-04", reason);

        assertThat(row("SELECT reason FROM days_off")).containsEntry("reason", reason);
    }

    private void set(final String date, final String reason) {
        webTestClient.put()
            .uri("/calendar/days-off/{date}", date)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("reason", reason))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.date").isEqualTo(date)
            .jsonPath("$.type").isEqualTo("day off")
            .jsonPath("$.reason").isEqualTo(reason);
    }

    private void notFound(final String date) {
        webTestClient.delete()
            .uri("/calendar/days-off/{date}", date)
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_DAY_OFF");
    }
}
