package cloud.cholewa.data.household.api;

import cloud.cholewa.data.config.ExceptionHandlerConfig;
import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.MemberSwitchedOffException;
import cloud.cholewa.data.household.config.HouseholdPushConfig;
import cloud.cholewa.data.household.service.PushSubscriptionService;
import cloud.cholewa.home.model.PushSubscription;
import cloud.cholewa.home.model.PushSubscriptionKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

//The push subscriptions of the household (HAS-272) through the real Jackson, the real validation and
//the real error mapping of the service
@Import({ExceptionHandlerConfig.class, HouseholdPushConfig.class})
@WebFluxTest(controllers = PushSubscriptionController.class, properties = "household.push.public-key=" + PushSubscriptionControllerTest.PUBLIC_KEY)
class PushSubscriptionControllerTest {

    static final String PUBLIC_KEY = "BA1b2_-A1b2_-A1b2_-A1b2_-A1b2_-A1b2_-A1b2_-A1b2_-A1b2_-A1b2_-A1b2_-A1b2_-A1b2_-A1b2_-xQ";
    private static final String P256DH = "B" + "c3D4-_".repeat(14) + "yZ";
    private static final String AUTH = "abcdefghijklmnopqrstuv";
    private static final String ENDPOINT = "https://push.example.com/send/abc123";

    private static final PushSubscription STORED = PushSubscription.builder()
        .endpoint(ENDPOINT)
        .keys(PushSubscriptionKeys.builder().p256dh(P256DH).auth(AUTH).build())
        .userAgent("Safari on iPhone")
        .createdAt(LocalDateTime.of(2026, 10, 24, 9, 30))
        .build();

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PushSubscriptionService pushSubscriptionService;

    @Test
    void should_answer_the_public_key_of_the_household() {
        webTestClient.get()
            .uri("/household/push-config")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .json("{\"publicKey\":\"" + PUBLIC_KEY + "\"}");
    }

    //the body a browser gives, as the application hands it over; createdAt is the registry's
    @Test
    void should_store_the_subscription_of_a_browser_and_answer_it() {
        when(pushSubscriptionService.store(eq("Ola"), any())).thenReturn(Mono.just(STORED));

        webTestClient.put()
            .uri("/household/member/Ola/push-subscriptions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"endpoint\":\"" + ENDPOINT + "\",\"keys\":{\"p256dh\":\"" + P256DH + "\",\"auth\":\"" + AUTH
                + "\"},\"userAgent\":\"Safari on iPhone\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.endpoint").isEqualTo(ENDPOINT)
            .jsonPath("$.keys.p256dh").isEqualTo(P256DH)
            .jsonPath("$.keys.auth").isEqualTo(AUTH)
            .jsonPath("$.createdAt").isEqualTo("2026-10-24T09:30:00");

        final ArgumentCaptor<PushSubscription> sent = ArgumentCaptor.forClass(PushSubscription.class);
        verify(pushSubscriptionService).store(eq("Ola"), sent.capture());
        assertThat(sent.getValue().getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(sent.getValue().getUserAgent()).isEqualTo("Safari on iPhone");
    }

    //what the model refuses never reaches the service: not https, a key of the wrong length, no keys
    @ParameterizedTest
    @ValueSource(strings = {
        "{\"endpoint\":\"http://push.example.com/x\",\"keys\":{\"p256dh\":\"" + "B" + "c3D4-_" + "\",\"auth\":\"x\"}}",
        "{\"endpoint\":\"https://push.example.com/x\",\"keys\":{\"p256dh\":\"short\",\"auth\":\"abcdefghijklmnopqrstuv\"}}",
        "{\"endpoint\":\"https://push.example.com/x\"}"
    })
    void should_refuse_a_subscription_the_model_does_not_take(final String body) {
        webTestClient.put()
            .uri("/household/member/Ola/push-subscriptions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange()
            .expectStatus().isBadRequest();

        verifyNoInteractions(pushSubscriptionService);
    }

    @Test
    void should_answer_404_for_an_unknown_member() {
        when(pushSubscriptionService.store(eq("Nobody"), any()))
            .thenReturn(Mono.error(HouseholdMemberNotFoundException.forName("Nobody")));

        webTestClient.put()
            .uri("/household/member/Nobody/push-subscriptions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(STORED)
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void should_answer_409_for_a_subscription_of_a_member_who_is_switched_off() {
        when(pushSubscriptionService.store(eq("Ola"), any()))
            .thenReturn(Mono.error(new MemberSwitchedOffException("Household member [Ola] is switched off")));

        webTestClient.put()
            .uri("/household/member/Ola/push-subscriptions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(STORED)
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("MEMBER_SWITCHED_OFF");
    }

    @Test
    void should_remove_a_subscription_by_its_endpoint() {
        when(pushSubscriptionService.remove("Ola", ENDPOINT)).thenReturn(Mono.empty());

        webTestClient.delete()
            .uri(uri -> uri.path("/household/member/Ola/push-subscriptions").queryParam("endpoint", ENDPOINT).build())
            .exchange()
            .expectStatus().isNoContent();

        verify(pushSubscriptionService).remove("Ola", ENDPOINT);
    }

    @Test
    void should_refuse_a_removal_without_an_endpoint() {
        webTestClient.delete()
            .uri("/household/member/Ola/push-subscriptions")
            .exchange()
            .expectStatus().isBadRequest();

        verifyNoInteractions(pushSubscriptionService);
    }

    //notification-service removes an endpoint its push service gave up on (HAS-276) - by the endpoint
    //alone; 204 whether it was there or not
    @Test
    void should_remove_a_dead_subscription_by_its_endpoint_alone() {
        when(pushSubscriptionService.removeByEndpoint(ENDPOINT)).thenReturn(Mono.empty());

        webTestClient.delete()
            .uri(uri -> uri.path("/push-subscriptions").queryParam("endpoint", ENDPOINT).build())
            .exchange()
            .expectStatus().isNoContent();

        verify(pushSubscriptionService).removeByEndpoint(ENDPOINT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void should_refuse_a_removal_of_a_dead_subscription_without_an_endpoint(final String endpoint) {
        webTestClient.delete()
            .uri(uri -> uri.path("/push-subscriptions").queryParam("endpoint", endpoint).build())
            .exchange()
            .expectStatus().isBadRequest();

        verifyNoInteractions(pushSubscriptionService);
    }

    @Test
    void should_refuse_a_removal_of_a_dead_subscription_with_no_endpoint_parameter() {
        webTestClient.delete()
            .uri("/push-subscriptions")
            .exchange()
            .expectStatus().isBadRequest();

        verifyNoInteractions(pushSubscriptionService);
    }

    @Test
    void should_not_reach_the_removal_of_notification_service_through_a_dot_segment_below_a_routed_prefix() {
        webTestClient.delete()
            .uri(java.net.URI.create("/household/../push-subscriptions?endpoint=" + ENDPOINT))
            .exchange()
            .expectStatus().isNotFound();

        verifyNoInteractions(pushSubscriptionService);
    }

    //the read of notification-service: the names as a list, the keys in the answer
    @Test
    void should_answer_the_subscriptions_of_the_named_members() {
        when(pushSubscriptionService.ofMembers(List.of("Ola", "Jan"))).thenReturn(Mono.just(List.of(STORED)));

        webTestClient.get()
            .uri("/push-subscriptions?members=Ola,Jan")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].endpoint").isEqualTo(ENDPOINT)
            .jsonPath("$[0].keys.auth").isEqualTo(AUTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/push-subscriptions", "/push-subscriptions?members=", "/push-subscriptions?members=a,,b"})
    void should_refuse_a_read_without_a_name(final String uri) {
        webTestClient.get()
            .uri(uri)
            .exchange()
            .expectStatus().isBadRequest();

        verifyNoInteractions(pushSubscriptionService);
    }

    //The read with the keys is hidden from outside only because no prefix the gateway routes leads to
    //it, and the gateway forwards a path as it was sent - /household/../push-subscriptions included.
    //That path must not reach the read here: this service does not resolve the dot segment
    @Test
    void should_not_reach_the_read_of_notification_service_through_a_dot_segment_below_a_routed_prefix() {
        webTestClient.get()
            .uri(java.net.URI.create("/household/../push-subscriptions?members=Ola"))
            .exchange()
            .expectStatus().isNotFound();

        verifyNoInteractions(pushSubscriptionService);
    }

    @Test
    void should_refuse_a_read_of_more_members_than_a_household_has() {
        final String many = String.join(",", java.util.Collections.nCopies(21, "Ola"));

        webTestClient.get()
            .uri("/push-subscriptions?members=" + many)
            .exchange()
            .expectStatus().isBadRequest();

        verifyNoInteractions(pushSubscriptionService);
    }
}
