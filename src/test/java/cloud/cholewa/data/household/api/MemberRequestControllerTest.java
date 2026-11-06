package cloud.cholewa.data.household.api;

import cloud.cholewa.data.config.ExceptionHandlerConfig;
import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.InvalidMemberRequestException;
import cloud.cholewa.data.error.MemberRequestNotFoundException;
import cloud.cholewa.data.error.MemberSwitchedOffException;
import cloud.cholewa.data.household.service.MemberRequestService;
import cloud.cholewa.home.model.MemberRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

//The household requests (HAS-409) through the real Jackson and the real error mapping of the service
@Import(ExceptionHandlerConfig.class)
@WebFluxTest(controllers = MemberRequestController.class)
class MemberRequestControllerTest {

    private static final MemberRequest STORED = MemberRequest.builder()
        .id(7L)
        .member("Anna")
        .text("Kup chleb")
        .author("Jan")
        .createdAt(LocalDateTime.of(2026, 11, 6, 18, 30))
        .build();

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private MemberRequestService memberRequestService;

    @Test
    void should_answer_the_open_requests_of_a_member_and_the_done_ones_when_asked() {
        when(memberRequestService.list("Anna", false)).thenReturn(Mono.just(List.of(STORED)));
        when(memberRequestService.list("Anna", true)).thenReturn(Mono.just(List.of()));

        webTestClient.get().uri("/household/member/Anna/requests").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].id").isEqualTo(7)
            .jsonPath("$[0].member").isEqualTo("Anna")
            .jsonPath("$[0].text").isEqualTo("Kup chleb")
            .jsonPath("$[0].author").isEqualTo("Jan")
            .jsonPath("$[0].createdAt").isEqualTo("2026-11-06T18:30:00")
            .jsonPath("$[0].sentAt").doesNotExist()
            .jsonPath("$[0].doneAt").doesNotExist();
        webTestClient.get().uri("/household/member/Anna/requests?done=true").exchange()
            .expectStatus().isOk()
            .expectBody().json("[]");
    }

    //201 with the stored request; the body is handed over as sent - the service reads text and author
    @Test
    void should_add_a_request_and_answer_it_with_201() {
        when(memberRequestService.create(eq("Anna"), any())).thenReturn(Mono.just(STORED));

        webTestClient.post().uri("/household/member/Anna/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"text\": \"Kup chleb\", \"author\": \"Jan\", \"doneBy\": \"Mallory\"}")
            .exchange()
            .expectStatus().isCreated()
            .expectBody().jsonPath("$.id").isEqualTo(7);

        final ArgumentCaptor<MemberRequest> sent = ArgumentCaptor.forClass(MemberRequest.class);
        verify(memberRequestService).create(eq("Anna"), sent.capture());
        assertThat(sent.getValue().getText()).isEqualTo("Kup chleb");
        assertThat(sent.getValue().getAuthor()).isEqualTo("Jan");
    }

    //not @Valid: a text of two lines reaches the rules, which answer with the code and the reason
    @Test
    void should_answer_a_refused_request_with_its_code_and_reason() {
        when(memberRequestService.create(eq("Anna"), any())).thenReturn(Mono.error(new InvalidMemberRequestException(
            "The text of a request is one line, without a line break, a tab or another control character")));

        webTestClient.post().uri("/household/member/Anna/requests")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"text\": \"Kup\\nchleb\", \"author\": \"Jan\"}")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("INVALID_REQUEST")
            .jsonPath("$.errors[0].message").isEqualTo("Invalid household request")
            .jsonPath("$.errors[0].details").isEqualTo(
                "The text of a request is one line, without a line break, a tab or another control character");
    }

    @Test
    void should_answer_an_unknown_and_a_switched_off_member_with_their_codes() {
        when(memberRequestService.create(eq("Nobody"), any()))
            .thenReturn(Mono.error(HouseholdMemberNotFoundException.forName("Nobody")));
        when(memberRequestService.create(eq("Ewa"), any()))
            .thenReturn(Mono.error(new MemberSwitchedOffException("Household member [Ewa] is switched off")));

        webTestClient.post().uri("/household/member/Nobody/requests").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"text\": \"x\", \"author\": \"Jan\"}").exchange()
            .expectStatus().isNotFound()
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_HOUSEHOLD_MEMBER");
        webTestClient.post().uri("/household/member/Ewa/requests").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"text\": \"x\", \"author\": \"Jan\"}").exchange()
            .expectStatus().isEqualTo(409)
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("MEMBER_SWITCHED_OFF");
    }

    @Test
    void should_tick_a_request_off_by_the_member_given() {
        when(memberRequestService.done("Anna", 7L, "Jan")).thenReturn(Mono.just(STORED));

        webTestClient.put().uri("/household/member/Anna/requests/7/done?member=Jan").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$.id").isEqualTo(7);
    }

    @Test
    void should_answer_a_request_not_on_the_list_with_its_code() {
        when(memberRequestService.done("Anna", 8L, "Jan"))
            .thenReturn(Mono.error(MemberRequestNotFoundException.forId("Anna", 8L)));
        when(memberRequestService.remove("Anna", 8L))
            .thenReturn(Mono.error(MemberRequestNotFoundException.forId("Anna", 8L)));

        webTestClient.put().uri("/household/member/Anna/requests/8/done?member=Jan").exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_REQUEST")
            .jsonPath("$.errors[0].details").isEqualTo("No request 8 on the list of household member [Anna]");
        webTestClient.delete().uri("/household/member/Anna/requests/8").exchange()
            .expectStatus().isNotFound()
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("NOT_FOUND_REQUEST");
    }

    @Test
    void should_remove_a_request_with_204() {
        when(memberRequestService.remove("Anna", 7L)).thenReturn(Mono.empty());

        webTestClient.delete().uri("/household/member/Anna/requests/7").exchange().expectStatus().isNoContent();
    }

    @Test
    void should_answer_the_pending_requests_for_notification_service_and_nothing_else() {
        when(memberRequestService.pending()).thenReturn(Mono.just(List.of(STORED)));

        webTestClient.get().uri("/household/requests?pending=true").exchange()
            .expectStatus().isOk()
            .expectBody().jsonPath("$[0].member").isEqualTo("Anna");
        webTestClient.get().uri("/household/requests?pending=false").exchange()
            .expectStatus().isBadRequest()
            .expectBody().jsonPath("$.errors[0].code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void should_mark_the_requests_sent_and_answer_the_number() {
        when(memberRequestService.markSent(List.of(7L, 8L))).thenReturn(Mono.just(1L));

        webTestClient.put().uri("/household/requests/sent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("[7, 8]")
            .exchange()
            .expectStatus().isOk()
            .expectBody().json("1");
    }

    @Test
    void should_refuse_more_than_five_hundred_ids_at_once() {
        final String ids = LongStream.rangeClosed(1, 501).mapToObj(Long::toString).toList().toString();

        webTestClient.put().uri("/household/requests/sent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(ids)
            .exchange()
            .expectStatus().isBadRequest();
        verifyNoInteractions(memberRequestService);
    }
}
