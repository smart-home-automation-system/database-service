package cloud.cholewa.data.postgres;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

//The household requests (HAS-409, V21) on a real PostgreSQL, through the API: the list of a member, ticking
//off once, the read of notification-service and marking what it texted, the retention on write, what a
//rename and a removal of a member leave behind, and the checks of the schema. Since HAS-415 (V23) the reads
//and the marks of the evening reminders and of what an author is told.
class MemberRequestsOnPostgresTest extends OnPostgres {

    @Autowired
    private WebTestClient webTestClient;

    @BeforeEach
    void registerTheMembers() {
        member("Anna", "+48111222333");
        member("Jan", "+48444555666");
    }

    @Test
    void should_store_a_request_and_answer_it_on_the_list_of_its_member() {
        add("anna", "  Kup chleb  ", "jan")
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.id").exists()
            .jsonPath("$.member").isEqualTo("Anna")
            .jsonPath("$.text").isEqualTo("Kup chleb")
            .jsonPath("$.author").isEqualTo("Jan")
            .jsonPath("$.createdAt").exists()
            .jsonPath("$.sentAt").doesNotExist();

        assertThat(texts(list("Anna", false))).containsExactly("Kup chleb");
        assertThat(texts(list("Jan", false))).isEmpty();
        assertThat(row("SELECT text, author, done_at FROM member_requests"))
            .containsEntry("text", "Kup chleb").containsEntry("author", "Jan").containsEntry("done_at", null);
    }

    //done twice keeps the first time; the open list leaves it out, the list with done=true has it
    @Test
    void should_tick_a_request_off_once() {
        final long id = idOf(add("Anna", "Kup chleb", "Jan"));

        done("Anna", id, "jan").expectStatus().isOk().expectBody().jsonPath("$.doneBy").isEqualTo("Jan");
        final Object first = row("SELECT done_at FROM member_requests").get("done_at");
        done("Anna", id, "Anna").expectStatus().isOk().expectBody().jsonPath("$.doneBy").isEqualTo("Jan");

        assertThat(row("SELECT done_at, done_by FROM member_requests")).containsEntry("done_at", first)
            .containsEntry("done_by", "Jan");
        assertThat(list("Anna", false)).isEmpty();
        assertThat(texts(list("Anna", true))).containsExactly("Kup chleb");
    }

    //done=true reads 30 days back, and the list of one member never shows another's
    @Test
    void should_show_the_done_requests_of_thirty_days_of_the_member_alone() {
        final long recent = idOf(add("Anna", "Kup chleb", "Jan"));
        final long old = idOf(add("Anna", "Kup mleko", "Jan"));
        final long other = idOf(add("Jan", "Odbierz paczkę", "Anna"));
        execute("UPDATE member_requests SET done_at = now() - interval '29 days', done_by = 'Jan' WHERE id IN ("
            + recent + ", " + other + ")");
        execute("UPDATE member_requests SET done_at = now() - interval '31 days', done_by = 'Jan' WHERE id = " + old);

        assertThat(texts(list("Anna", true))).containsExactly("Kup chleb");
        assertThat(texts(list("Anna", false))).isEmpty();
        assertThat(texts(list("Jan", true))).containsExactly("Odbierz paczkę");
    }

    //a request is found on the list of its own member only
    @Test
    void should_not_reach_a_request_through_another_member() {
        final long id = idOf(add("Anna", "Kup chleb", "Jan"));

        done("Jan", id, "Jan").expectStatus().isNotFound()
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_REQUEST");
        webTestClient.delete().uri("/household/member/Jan/requests/{id}", id).exchange().expectStatus().isNotFound();
        webTestClient.delete().uri("/household/member/Anna/requests/{id}", id).exchange().expectStatus().isNoContent();
        assertThat(rows("SELECT id FROM member_requests")).isEmpty();
    }

    //every open request not yet texted of every member switched on, by member and oldest first; marking
    //them sent skips what was sent before and an unknown id
    @Test
    void should_answer_the_pending_requests_and_mark_them_sent_once() {
        final long first = idOf(add("Jan", "Odbierz paczkę", "Anna"));
        final long second = idOf(add("Anna", "Kup chleb", "Jan"));
        final long third = idOf(add("Anna", "Kup mleko", "Jan"));
        final long done = idOf(add("Anna", "Wynieś śmieci", "Jan"));
        done("Anna", done, "Anna").expectStatus().isOk();

        assertThat(pending()).extracting(request -> request.get("text"))
            .containsExactly("Kup chleb", "Kup mleko", "Odbierz paczkę");

        sent(List.of(second, third, 999_999L)).expectBody().json("2");
        sent(List.of(second, first)).expectBody().json("1");

        assertThat(pending()).isEmpty();
        assertThat(rows("SELECT id FROM member_requests WHERE sent_at IS NOT NULL")).hasSize(3);
    }

    @Test
    void should_leave_the_requests_of_a_member_switched_off_out_of_the_pending_ones() {
        add("Jan", "Odbierz paczkę", "Anna").expectStatus().isCreated();
        webTestClient.post().uri("/household/member/Jan/deactivate").exchange().expectStatus().isOk();

        assertThat(pending()).isEmpty();
        add("Jan", "Kup chleb", "Anna").expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("MEMBER_SWITCHED_OFF");
        add("Anna", "Kup chleb", "Jan").expectStatus().isBadRequest()
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("INVALID_REQUEST");
    }

    //the retention without a scheduler: a request done more than 30 days ago goes with the next write - once
    //its author was told (HAS-415)
    @Test
    void should_delete_the_requests_done_more_than_thirty_days_ago_with_the_next_write() {
        final long old = idOf(add("Anna", "Kup chleb", "Jan"));
        final long recent = idOf(add("Anna", "Kup mleko", "Jan"));
        final long unannounced = idOf(add("Anna", "Odbierz paczkę", "Jan"));
        execute("UPDATE member_requests SET done_at = now() - interval '31 days', done_by = 'Jan',"
            + " done_announced_at = now() WHERE id = " + old);
        execute("UPDATE member_requests SET done_at = now() - interval '29 days', done_by = 'Jan',"
            + " done_announced_at = now() WHERE id = " + recent);
        execute("UPDATE member_requests SET done_at = now() - interval '31 days', done_by = 'Anna' WHERE id = " + unannounced);

        add("Anna", "Wynieś śmieci", "Jan").expectStatus().isCreated();

        assertThat(rows("SELECT id FROM member_requests WHERE id = " + old)).isEmpty();
        assertThat(rows("SELECT id FROM member_requests WHERE id = " + recent)).hasSize(1);
        assertThat(rows("SELECT id FROM member_requests WHERE id = " + unannounced)).hasSize(1);
    }

    //the list follows its member by id through a rename, and goes with a member removed
    @Test
    void should_keep_the_list_through_a_rename_and_drop_it_with_the_member() {
        add("Anna", "Kup chleb", "Jan").expectStatus().isCreated();

        webTestClient.patch().uri("/household/member/Anna").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\": \"Annabel\", \"phone\": \"+48111222333\", \"active\": true}").exchange()
            .expectStatus().isOk();
        assertThat(texts(list("Annabel", false))).containsExactly("Kup chleb");

        webTestClient.delete().uri("/household/member/Annabel").exchange().expectStatus().is2xxSuccessful();
        assertThat(rows("SELECT id FROM member_requests")).isEmpty();
    }

    @Test
    void should_refuse_a_text_of_two_lines_or_too_long_with_the_code() {
        add("Anna", "Kup\nchleb", "Jan").expectStatus().isBadRequest()
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("INVALID_REQUEST");
        add("Anna", "a".repeat(101), "Jan").expectStatus().isBadRequest()
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("INVALID_REQUEST");
        assertThat(rows("SELECT id FROM member_requests")).isEmpty();
    }

    //the checks of V21 hold past the API too
    @Test
    void should_refuse_in_the_schema_what_the_rules_refuse() {
        assertThatThrownBy(() -> execute("""
            INSERT INTO member_requests (member_id, text, author, created_at)
            SELECT id, E'Kup\\nchleb', 'Jan', now() FROM household_members WHERE name = 'Anna'"""))
            .hasMessageContaining("member_requests_text_one_line");
        assertThatThrownBy(() -> execute("""
            INSERT INTO member_requests (member_id, text, author, created_at, done_at)
            SELECT id, 'Kup chleb', 'Jan', now(), now() FROM household_members WHERE name = 'Anna'"""))
            .hasMessageContaining("member_requests_done_together");
        assertThatThrownBy(() -> execute("""
            INSERT INTO member_requests (member_id, text, author, created_at)
            SELECT id, 'Kup chleb', 'Jo', now() FROM household_members WHERE name = 'Anna'"""))
            .hasMessageContaining("member_requests_names");
    }

    //HAS-415: ticked off by its own author it is announced at once, by anybody else it waits for
    //notification-service - and once marked it is never answered again
    @Test
    void should_announce_a_request_ticked_off_by_another_member_once_and_never_one_of_its_own_author() {
        final long byOther = idOf(add("Anna", "Kup chleb", "Jan"));
        final long byAuthor = idOf(add("Anna", "Kup mleko", "Jan"));
        final long open = idOf(add("Anna", "Wynieś śmieci", "Jan"));
        done("Anna", byOther, "Anna").expectStatus().isOk();
        done("Anna", byAuthor, "JAN").expectStatus().isOk().expectBody().jsonPath("$.doneAnnouncedAt").exists();

        assertThat(read("/household/requests/done-to-announce")).extracting(request -> request.get("text"),
            request -> request.get("member"), request -> request.get("doneBy"))
            .containsExactly(tuple("Kup chleb", "Anna", "Anna"));

        mark("done-announced", List.of(byOther, byAuthor, open, 999_999L)).expectBody().json("1");
        mark("done-announced", List.of(byOther)).expectBody().json("0");
        assertThat(read("/household/requests/done-to-announce")).isEmpty();
        assertThat(row("SELECT done_announced_at FROM member_requests WHERE id = " + open))
            .containsEntry("done_announced_at", null);
    }

    //HAS-415: the open texted requests of the members switched on; with remindedBefore only those not
    //reminded since then. The mark moves on with every evening and skips a request done or never texted
    @Test
    void should_answer_the_requests_to_remind_and_mark_the_reminder() {
        final long texted = idOf(add("Anna", "Kup chleb", "Jan"));
        final long notTexted = idOf(add("Anna", "Kup mleko", "Jan"));
        final long done = idOf(add("Jan", "Odbierz paczkę", "Anna"));
        sent(List.of(texted, done)).expectBody().json("2");
        done("Jan", done, "Jan").expectStatus().isOk();

        assertThat(texts(read("/household/requests/to-remind"))).containsExactly("Kup chleb");

        mark("reminded", List.of(texted, notTexted, done)).expectBody().json("1");
        assertThat(texts(read("/household/requests/to-remind?remindedBefore=2000-01-01T00:00"))).isEmpty();
        assertThat(texts(read("/household/requests/to-remind?remindedBefore=2999-01-01T00:00"))).containsExactly("Kup chleb");
        assertThat(texts(read("/household/requests/to-remind"))).containsExactly("Kup chleb");

        webTestClient.post().uri("/household/member/Anna/deactivate").exchange().expectStatus().isOk();
        assertThat(read("/household/requests/to-remind")).isEmpty();
    }

    //HAS-415: open, texted before the time given, its author never told - and told once
    @Test
    void should_answer_the_stale_requests_once() {
        final long old = idOf(add("Anna", "Kup chleb", "Jan"));
        final long recent = idOf(add("Anna", "Kup mleko", "Jan"));
        final long oldDone = idOf(add("Anna", "Wynieś śmieci", "Jan"));
        sent(List.of(old, recent, oldDone)).expectBody().json("3");
        execute("UPDATE member_requests SET sent_at = now() - interval '4 days' WHERE id IN (" + old + ", " + oldDone + ")");
        done("Anna", oldDone, "Anna").expectStatus().isOk();

        final String sentBefore = "/household/requests/stale?sentBefore=" + LocalDateTime.now(ZoneId.systemDefault()).minusDays(3)
            .withNano(0);
        assertThat(read(sentBefore)).extracting(request -> request.get("text"), request -> request.get("author"))
            .containsExactly(tuple("Kup chleb", "Jan"));

        mark("stale-announced", List.of(old)).expectBody().json("1");
        mark("stale-announced", List.of(old)).expectBody().json("0");
        assertThat(read(sentBefore)).isEmpty();
        webTestClient.get().uri("/household/requests/stale").exchange().expectStatus().isBadRequest()
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("INVALID_REQUEST");
    }

    //the check of V23 holds past the API too: a mark only where it may stand
    @Test
    void should_refuse_a_mark_in_the_schema_where_it_cannot_stand() {
        final long open = idOf(add("Anna", "Kup chleb", "Jan"));

        assertThatThrownBy(() -> execute("UPDATE member_requests SET done_announced_at = now() WHERE id = " + open))
            .hasMessageContaining("member_requests_marks");
        assertThatThrownBy(() -> execute("UPDATE member_requests SET reminded_at = now() WHERE id = " + open))
            .hasMessageContaining("member_requests_marks");
        assertThatThrownBy(() -> execute("UPDATE member_requests SET stale_announced_at = now() WHERE id = " + open))
            .hasMessageContaining("member_requests_marks");
    }

    private WebTestClient.ResponseSpec mark(final String what, final List<Long> ids) {
        return webTestClient.put()
            .uri("/household/requests/" + what)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ids)
            .exchange()
            .expectStatus().isOk();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> read(final String uri) {
        return (List) webTestClient.get()
            .uri(uri)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Map.class)
            .returnResult()
            .getResponseBody();
    }

    private void member(final String name, final String phone) {
        webTestClient.post()
            .uri("/household/member")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\": \"" + name + "\", \"phone\": \"" + phone + "\", \"active\": true}")
            .exchange()
            .expectStatus().isCreated();
    }

    private WebTestClient.ResponseSpec add(final String member, final String text, final String author) {
        return webTestClient.post()
            .uri("/household/member/{name}/requests", member)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("text", text, "author", author))
            .exchange();
    }

    private WebTestClient.ResponseSpec done(final String member, final long id, final String who) {
        return webTestClient.put()
            .uri(uri -> uri.path("/household/member/{name}/requests/{id}/done").queryParam("member", who).build(member, id))
            .exchange();
    }

    private WebTestClient.ResponseSpec sent(final List<Long> ids) {
        return webTestClient.put()
            .uri("/household/requests/sent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ids)
            .exchange()
            .expectStatus().isOk();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> list(final String member, final boolean done) {
        return (List) webTestClient.get()
            .uri("/household/member/{name}/requests?done={done}", member, done)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Map.class)
            .returnResult()
            .getResponseBody();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Map<String, Object>> pending() {
        return (List) webTestClient.get()
            .uri("/household/requests?pending=true")
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Map.class)
            .returnResult()
            .getResponseBody();
    }

    private static List<Object> texts(final List<Map<String, Object>> requests) {
        return requests.stream().map(request -> request.get("text")).toList();
    }

    private static long idOf(final WebTestClient.ResponseSpec response) {
        final Map<?, ?> body = response.expectStatus().isCreated().expectBody(Map.class).returnResult().getResponseBody();
        return ((Number) body.get("id")).longValue();
    }
}
