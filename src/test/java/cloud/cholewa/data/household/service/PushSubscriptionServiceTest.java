package cloud.cholewa.data.household.service;

import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.MemberSwitchedOffException;
import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import cloud.cholewa.data.household.model.PushSubscriptionEntity;
import cloud.cholewa.data.household.repository.HouseholdMemberRepository;
import cloud.cholewa.data.household.repository.PushSubscriptionRepository;
import cloud.cholewa.home.model.MemberRole;
import cloud.cholewa.home.model.PushSubscription;
import cloud.cholewa.home.model.PushSubscriptionKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 8, 15, 12, 0);
    private static final String ENDPOINT = "https://push.example.com/send/abc123";
    private static final String P256DH = "B" + "c3D4-_".repeat(14) + "yZ";
    private static final String AUTH = "abcdefghijklmnopqrstuv";

    private static final HouseholdMemberEntity OLA = member(true);

    private static final PushSubscription SUBSCRIPTION = PushSubscription.builder()
        .endpoint(ENDPOINT)
        .keys(PushSubscriptionKeys.builder().p256dh(P256DH).auth(AUTH).build())
        .userAgent("Safari on iPhone")
        //what a client says of the time is not the registry's: ignored
        .createdAt(LocalDateTime.of(2000, 1, 1, 0, 0))
        .build();

    @Mock
    private HouseholdMemberRepository householdMemberRepository;

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @InjectMocks
    private PushSubscriptionService sut;

    @Test
    void should_store_a_subscription_for_the_member_with_the_time_of_the_registry() {
        when(householdMemberRepository.findByNameIgnoreCase("ola")).thenReturn(Mono.just(OLA));
        when(pushSubscriptionRepository.store(any())).thenAnswer(invocation -> {
            final PushSubscriptionEntity sent = invocation.getArgument(0);
            return Mono.just(PushSubscriptionEntity.builder().id(3L).memberId(sent.getMemberId())
                .endpoint(sent.getEndpoint()).p256dh(sent.getP256dh()).auth(sent.getAuth())
                .userAgent(sent.getUserAgent()).createdAt(sent.getCreatedAt()).build());
        });

        sut.store("ola", SUBSCRIPTION)
            .as(StepVerifier::create)
            .assertNext(stored -> {
                assertThat(stored.getEndpoint()).isEqualTo(ENDPOINT);
                assertThat(stored.getKeys().getP256dh()).isEqualTo(P256DH);
                assertThat(stored.getKeys().getAuth()).isEqualTo(AUTH);
                assertThat(stored.getUserAgent()).isEqualTo("Safari on iPhone");
                assertThat(stored.getCreatedAt()).isAfter(LocalDateTime.now(ZoneId.systemDefault()).minusMinutes(1));
            })
            .verifyComplete();

        final ArgumentCaptor<PushSubscriptionEntity> entity = ArgumentCaptor.forClass(PushSubscriptionEntity.class);
        verify(pushSubscriptionRepository).store(entity.capture());
        assertThat(entity.getValue().getId()).isNull();
        assertThat(entity.getValue().getMemberId()).isEqualTo(7L);
        assertThat(entity.getValue().getLastUsedAt()).isNull();
    }

    @Test
    void should_refuse_a_subscription_of_an_unknown_member() {
        when(householdMemberRepository.findByNameIgnoreCase("Nobody")).thenReturn(Mono.empty());

        sut.store("Nobody", SUBSCRIPTION)
            .as(StepVerifier::create)
            .expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(HouseholdMemberNotFoundException.class)
                .hasMessage("No household member named [Nobody]"))
            .verify();

        verify(pushSubscriptionRepository, never()).store(any());
    }

    //switching a member off clears the subscriptions; one must not come back through a browser
    @Test
    void should_refuse_a_subscription_of_a_member_who_is_switched_off() {
        when(householdMemberRepository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(member(false)));

        sut.store("Ola", SUBSCRIPTION)
            .as(StepVerifier::create)
            .expectErrorSatisfies(error -> assertThat(error)
                .isInstanceOf(MemberSwitchedOffException.class)
                .hasMessage("Household member [Ola] is switched off and takes no push subscription"))
            .verify();

        verify(pushSubscriptionRepository, never()).store(any());
    }

    //nothing to remove is no error - a browser may unsubscribe twice
    @Test
    void should_remove_a_subscription_of_the_member_and_take_none_as_done() {
        when(householdMemberRepository.findByNameIgnoreCase("Ola")).thenReturn(Mono.just(OLA));
        when(pushSubscriptionRepository.deleteOfMember(7L, ENDPOINT)).thenReturn(Mono.just(0L));

        sut.remove("Ola", ENDPOINT)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(pushSubscriptionRepository).deleteOfMember(7L, ENDPOINT);
    }

    @Test
    void should_refuse_a_removal_for_an_unknown_member() {
        when(householdMemberRepository.findByNameIgnoreCase("Nobody")).thenReturn(Mono.empty());

        sut.remove("Nobody", ENDPOINT)
            .as(StepVerifier::create)
            .expectError(HouseholdMemberNotFoundException.class)
            .verify();

        verify(pushSubscriptionRepository, never()).deleteOfMember(anyLong(), any());
    }

    //a dead endpoint is removed whoever it belonged to; two messages may find it, so nothing to remove
    //is no error (HAS-276)
    @Test
    void should_remove_a_dead_subscription_by_its_endpoint_and_take_none_as_done() {
        when(pushSubscriptionRepository.deleteByEndpoint(ENDPOINT)).thenReturn(Mono.just(1L), Mono.just(0L));

        sut.removeByEndpoint(ENDPOINT)
            .as(StepVerifier::create)
            .verifyComplete();
        sut.removeByEndpoint(ENDPOINT)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(pushSubscriptionRepository, times(2)).deleteByEndpoint(ENDPOINT);
        verifyNoInteractions(householdMemberRepository);
    }

    @Test
    void should_answer_the_subscriptions_of_the_named_members_as_the_query_orders_them() {
        when(pushSubscriptionRepository.findAllOfActiveMembers(List.of("Ola", "Jan"))).thenReturn(Flux.just(
            entity(1L, "https://push.example.com/a"),
            entity(2L, "https://push.example.com/b")
        ));

        sut.ofMembers(List.of("Ola", "Jan"))
            .as(StepVerifier::create)
            .assertNext(subscriptions -> assertThat(subscriptions)
                .extracting(PushSubscription::getEndpoint)
                .containsExactly("https://push.example.com/a", "https://push.example.com/b"))
            .verifyComplete();
    }

    @Test
    void should_answer_none_for_members_without_a_subscription() {
        when(pushSubscriptionRepository.findAllOfActiveMembers(List.of("Jan"))).thenReturn(Flux.empty());

        sut.ofMembers(List.of("Jan"))
            .as(StepVerifier::create)
            .assertNext(subscriptions -> assertThat(subscriptions).isEmpty())
            .verifyComplete();
    }

    private static PushSubscriptionEntity entity(final Long id, final String endpoint) {
        return PushSubscriptionEntity.builder().id(id).memberId(7L).endpoint(endpoint).p256dh(P256DH).auth(AUTH)
            .createdAt(CREATED_AT).build();
    }

    private static HouseholdMemberEntity member(final boolean active) {
        return new HouseholdMemberEntity(7L, CREATED_AT, null, "Ola", "+48111222333", active, MemberRole.RESIDENT,
            List.of(), List.of());
    }
}
