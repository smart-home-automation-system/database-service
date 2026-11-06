package cloud.cholewa.data.household.service;

import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.InvalidMemberRequestException;
import cloud.cholewa.data.error.MemberRequestNotFoundException;
import cloud.cholewa.data.error.MemberSwitchedOffException;
import cloud.cholewa.data.household.mapper.MemberRequestMapperImpl;
import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import cloud.cholewa.data.household.model.MemberRequestEntity;
import cloud.cholewa.data.household.repository.HouseholdMemberRepository;
import cloud.cholewa.data.household.repository.MemberRequestRepository;
import cloud.cholewa.data.household.repository.MemberRequestRepository.PendingRequest;
import cloud.cholewa.home.model.MemberRequest;
import cloud.cholewa.home.model.MemberRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

//The household requests (HAS-409): who may write, who receives, ticking off twice, the list of
//notification-service - on the real generated mapper
class MemberRequestServiceTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 6, 18, 30);

    private final HouseholdMemberRepository members = mock(HouseholdMemberRepository.class);
    private final MemberRequestRepository requests = mock(MemberRequestRepository.class);
    private MemberRequestService sut;

    private static HouseholdMemberEntity member(final long id, final String name, final boolean active) {
        return new HouseholdMemberEntity(id, CREATED, null, name, "+48500000001", active, MemberRole.RESIDENT, List.of(),
            List.of());
    }

    private static MemberRequestEntity row(final long id) {
        return MemberRequestEntity.builder().id(id).memberId(1L).text("Kup chleb").author("Jan").createdAt(CREATED).build();
    }

    @BeforeEach
    void setUp() {
        when(members.findByNameIgnoreCase(anyString())).thenReturn(Mono.empty());
        when(members.findByNameIgnoreCase("anna")).thenReturn(Mono.just(member(1, "Anna", true)));
        when(members.findByNameIgnoreCase("jan")).thenReturn(Mono.just(member(2, "Jan", true)));
        when(members.findByNameIgnoreCase("Jan")).thenReturn(Mono.just(member(2, "Jan", true)));
        when(members.findByNameIgnoreCase("ewa")).thenReturn(Mono.just(member(3, "Ewa", false)));
        when(requests.deleteDoneBefore(anyLong(), any())).thenReturn(Mono.just(0L));
        when(requests.save(any())).thenAnswer(call -> Mono.just(((MemberRequestEntity) call.getArgument(0)).withId(7L)));
        sut = new MemberRequestService(members, requests, new MemberRequestMapperImpl());
    }

    private static MemberRequest body(final String text, final String author) {
        return MemberRequest.builder().text(text).author(author).build();
    }

    //the text stripped, the author as the registry spells it, the time of the server - nothing else of the body
    @Test
    void should_store_a_request_with_the_author_as_the_registry_spells_it() {
        final MemberRequest sent = MemberRequest.builder().text("  Kup chleb ").author("jan").doneBy("Mallory")
            .sentAt(CREATED).member("Ewa").build();

        sut.create("anna", sent).as(StepVerifier::create)
            .assertNext(stored -> {
                assertThat(stored.getId()).isEqualTo(7L);
                assertThat(stored.getMember()).isEqualTo("Anna");
                assertThat(stored.getText()).isEqualTo("Kup chleb");
                assertThat(stored.getAuthor()).isEqualTo("Jan");
                assertThat(stored.getCreatedAt()).isNotNull();
                assertThat(stored.getSentAt()).isNull();
                assertThat(stored.getDoneBy()).isNull();
            })
            .verifyComplete();

        final ArgumentCaptor<MemberRequestEntity> saved = ArgumentCaptor.forClass(MemberRequestEntity.class);
        verify(requests).save(saved.capture());
        assertThat(saved.getValue().getMemberId()).isEqualTo(1L);
    }

    //the retention without a scheduler: the done requests older than 30 days go with the next write
    @Test
    void should_delete_the_done_requests_older_than_thirty_days_with_a_write() {
        sut.create("anna", body("Kup chleb", "Jan")).as(StepVerifier::create).expectNextCount(1).verifyComplete();

        final ArgumentCaptor<LocalDateTime> before = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(requests).deleteDoneBefore(eq(1L), before.capture());
        assertThat(before.getValue()).isBefore(LocalDateTime.now().minusDays(29)).isAfter(LocalDateTime.now().minusDays(31));
    }

    @Test
    void should_refuse_an_author_who_is_no_member_switched_on() {
        sut.create("anna", body("Kup chleb", "Mallory")).as(StepVerifier::create)
            .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(InvalidMemberRequestException.class)
                .hasMessage("The author [Mallory] is no household member switched on"))
            .verify();
        sut.create("anna", body("Kup chleb", "ewa")).as(StepVerifier::create)
            .expectError(InvalidMemberRequestException.class)
            .verify();
        verify(requests, never()).save(any());
    }

    @Test
    void should_refuse_a_recipient_unknown_or_switched_off() {
        sut.create("nobody", body("Kup chleb", "Jan")).as(StepVerifier::create)
            .expectError(HouseholdMemberNotFoundException.class)
            .verify();
        sut.create("ewa", body("Kup chleb", "Jan")).as(StepVerifier::create)
            .expectError(MemberSwitchedOffException.class)
            .verify();
        verify(requests, never()).save(any());
    }

    //the text is checked before any query
    @Test
    void should_refuse_a_text_before_asking_the_registry() {
        sut.create("anna", body("Kup\nchleb", "Jan")).as(StepVerifier::create)
            .expectError(InvalidMemberRequestException.class)
            .verify();
        verify(members, never()).findByNameIgnoreCase(anyString());
    }

    @Test
    void should_list_the_open_requests_or_with_the_done_ones_of_thirty_days() {
        when(requests.findListOfMember(eq(1L), isNull())).thenReturn(Flux.just(row(7)));
        when(requests.findListOfMember(eq(1L), any(LocalDateTime.class))).thenReturn(Flux.just(row(7), row(8)));

        sut.list("anna", false).as(StepVerifier::create)
            .assertNext(list -> assertThat(list).extracting(MemberRequest::getId).containsExactly(7L))
            .verifyComplete();
        sut.list("anna", true).as(StepVerifier::create)
            .assertNext(list -> assertThat(list).extracting(MemberRequest::getMember).containsExactly("Anna", "Anna"))
            .verifyComplete();
    }

    //done twice keeps the first: the update changes nothing, the stored request is answered
    @Test
    void should_tick_a_request_off_once_and_answer_it_again_when_done_twice() {
        final MemberRequestEntity done = row(7).withDoneAt(CREATED.plusHours(1)).withDoneBy("Jan");
        when(requests.markDone(eq(1L), eq(7L), any(), eq("Jan"))).thenReturn(Mono.just(1L), Mono.just(0L));
        when(requests.findOneOfMember(1L, 7L)).thenReturn(Mono.just(done));

        sut.done("anna", 7L, "jan").as(StepVerifier::create)
            .assertNext(request -> assertThat(request.getDoneBy()).isEqualTo("Jan"))
            .verifyComplete();
        sut.done("anna", 7L, "jan").as(StepVerifier::create)
            .assertNext(request -> assertThat(request.getDoneAt()).isEqualTo(CREATED.plusHours(1)))
            .verifyComplete();
    }

    @Test
    void should_answer_a_request_not_on_the_list() {
        when(requests.markDone(anyLong(), anyLong(), any(), anyString())).thenReturn(Mono.just(0L));
        when(requests.findOneOfMember(1L, 9L)).thenReturn(Mono.empty());
        when(requests.deleteOfMember(1L, 9L)).thenReturn(Mono.just(0L));

        sut.done("anna", 9L, "Jan").as(StepVerifier::create)
            .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(MemberRequestNotFoundException.class)
                .hasMessage("No request 9 on the list of household member [anna]"))
            .verify();
        sut.remove("anna", 9L).as(StepVerifier::create).expectError(MemberRequestNotFoundException.class).verify();
    }

    @Test
    void should_refuse_a_tick_by_somebody_who_is_no_member() {
        sut.done("anna", 7L, "Mallory").as(StepVerifier::create)
            .expectErrorSatisfies(error -> assertThat(error)
                .hasMessage("The member who ticked it off [Mallory] is no household member switched on"))
            .verify();
        verify(requests, never()).markDone(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    void should_remove_a_request_of_the_list() {
        when(requests.deleteOfMember(1L, 7L)).thenReturn(Mono.just(1L));

        sut.remove("anna", 7L).as(StepVerifier::create).verifyComplete();
    }

    @Test
    void should_answer_the_pending_requests_with_the_names_of_their_members() {
        when(requests.findPending()).thenReturn(Flux.just(new PendingRequest(row(7), "Anna"), new PendingRequest(row(8), "Jan")));

        sut.pending().as(StepVerifier::create)
            .assertNext(list -> assertThat(list).extracting(MemberRequest::getMember).containsExactly("Anna", "Jan"))
            .verifyComplete();
    }

    @Test
    void should_mark_the_requests_sent_and_refuse_no_ids() {
        when(requests.markSent(eq(List.of(7L, 8L)), any())).thenReturn(Mono.just(2L));

        sut.markSent(List.of(7L, 8L)).as(StepVerifier::create).expectNext(2L).verifyComplete();
        sut.markSent(List.of()).as(StepVerifier::create).expectError(InvalidMemberRequestException.class).verify();
        sut.markSent(null).as(StepVerifier::create).expectError(InvalidMemberRequestException.class).verify();
        sut.markSent(Arrays.asList(7L, null)).as(StepVerifier::create).expectError(InvalidMemberRequestException.class).verify();
    }
}
