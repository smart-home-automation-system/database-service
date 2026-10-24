package cloud.cholewa.data.postgres;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//The push subscriptions of the household (HAS-272, V19) on a real PostgreSQL, through the API: the
//upsert by the endpoint, the read of notification-service with its names compared the way the
//registry keeps them unique, and what switching off and removing a member leave behind.
class PushSubscriptionsOnPostgresTest extends OnPostgres {

    private static final String P256DH = "B" + "c3D4-_".repeat(14) + "yZ";
    private static final String OTHER_P256DH = "B" + "e5F6_-".repeat(14) + "wX";
    private static final String AUTH = "abcdefghijklmnopqrstuv";
    private static final String ENDPOINT = "https://push.example.com/send/abc123";

    @Autowired
    private WebTestClient webTestClient;

    @BeforeEach
    void registerTheMembers() {
        member("Anna", "+48111222333");
        member("Jan", "+48444555666");
    }

    @Test
    void should_store_a_subscription_of_a_member_and_answer_it_with_the_time_of_the_registry() {
        stored("Anna", subscription(ENDPOINT, P256DH, "Safari on iPhone"))
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.endpoint").isEqualTo(ENDPOINT)
            .jsonPath("$.keys.p256dh").isEqualTo(P256DH)
            .jsonPath("$.keys.auth").isEqualTo(AUTH)
            .jsonPath("$.userAgent").isEqualTo("Safari on iPhone")
            .jsonPath("$.createdAt").exists();

        assertThat(subscriptionOf(ENDPOINT))
            .containsEntry("member", "Anna")
            .containsEntry("p256dh", P256DH)
            .containsEntry("auth", AUTH)
            .containsEntry("user_agent", "Safari on iPhone")
            .containsEntry("last_used_at", null);
    }

    //a browser that subscribes again is the same row: new keys, the first time kept; on a shared
    //tablet another member subscribing takes the browser over
    @Test
    void should_keep_one_row_per_endpoint_and_let_another_member_take_a_browser_over() {
        stored("Anna", subscription(ENDPOINT, P256DH, "Safari on iPhone")).expectStatus().isOk();
        final Object firstStored = subscriptionOf(ENDPOINT).get("created_at");
        final Object id = subscriptionOf(ENDPOINT).get("id");

        stored("anna", subscription(ENDPOINT, OTHER_P256DH, null)).expectStatus().isOk();
        assertThat(subscriptionOf(ENDPOINT))
            .containsEntry("id", id)
            .containsEntry("member", "Anna")
            .containsEntry("p256dh", OTHER_P256DH)
            .containsEntry("user_agent", null)
            .containsEntry("created_at", firstStored);

        stored("Jan", subscription(ENDPOINT, OTHER_P256DH, "Safari on iPad")).expectStatus().isOk();
        assertThat(subscriptionOf(ENDPOINT)).containsEntry("id", id).containsEntry("member", "Jan");
        assertThat(count()).isEqualTo(1);
    }

    //the names as the registry keeps them unique - ignoring case - and only the members switched on
    @Test
    void should_read_the_subscriptions_of_the_named_members_who_are_switched_on() {
        stored("Anna", subscription("https://push.example.com/a", P256DH, null)).expectStatus().isOk();
        stored("Jan", subscription("https://push.example.com/b", P256DH, null)).expectStatus().isOk();
        stored("Jan", subscription("https://push.example.com/c", P256DH, null)).expectStatus().isOk();

        assertThat(endpointsOf("ANNA,jan")).containsExactly(
            "https://push.example.com/a", "https://push.example.com/b", "https://push.example.com/c");
        assertThat(endpointsOf("Jan,Nobody")).containsExactly("https://push.example.com/b", "https://push.example.com/c");
        assertThat(endpointsOf("Nobody")).isEmpty();

        execute("UPDATE household_members SET active = false WHERE name = 'Jan'");
        assertThat(endpointsOf("Anna,Jan")).containsExactly("https://push.example.com/a");
    }

    //switched off, a member keeps no subscription and takes none; removed, they go with the member
    @Test
    void should_clear_the_subscriptions_of_a_member_switched_off_and_of_one_removed() {
        stored("Anna", subscription("https://push.example.com/a", P256DH, null)).expectStatus().isOk();
        stored("Jan", subscription("https://push.example.com/b", P256DH, null)).expectStatus().isOk();

        webTestClient.post().uri("/household/member/Jan/deactivate").exchange().expectStatus().isOk();
        assertThat(rows("SELECT endpoint FROM push_subscriptions"))
            .extracting(row -> row.get("endpoint"))
            .containsExactly("https://push.example.com/a");
        stored("Jan", subscription("https://push.example.com/b", P256DH, null))
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.errors[0].code").isEqualTo("MEMBER_SWITCHED_OFF");

        webTestClient.delete().uri("/household/member/Anna").exchange().expectStatus().isNoContent();
        assertThat(count()).isZero();
    }

    @Test
    void should_remove_a_subscription_of_a_member_and_none_of_another() {
        stored("Anna", subscription("https://push.example.com/a", P256DH, null)).expectStatus().isOk();
        stored("Jan", subscription("https://push.example.com/b", P256DH, null)).expectStatus().isOk();

        //Jan has no such subscription: nothing goes, and no error
        removed("Jan", "https://push.example.com/a");
        assertThat(count()).isEqualTo(2);

        removed("Anna", "https://push.example.com/a");
        removed("Anna", "https://push.example.com/a");
        assertThat(rows("SELECT endpoint FROM push_subscriptions"))
            .extracting(row -> row.get("endpoint"))
            .containsExactly("https://push.example.com/b");
    }

    //no key in the configuration of the tests: the application is told there is none
    @Test
    void should_answer_no_public_key_when_none_is_configured() {
        webTestClient.get()
            .uri("/household/push-config")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .json("{}", org.springframework.test.json.JsonCompareMode.STRICT);
    }

    //the schema keeps what the model keeps, for a row written past the API
    @Test
    void should_refuse_an_endpoint_that_is_not_https_and_keys_that_are_no_base64url_in_the_schema() {
        assertThatThrownBy(() -> execute("""
            INSERT INTO push_subscriptions (member_id, endpoint, p256dh, auth, created_at)
            SELECT id, 'http://push.example.com/a', '%s', '%s', now() FROM household_members WHERE name = 'Anna'"""
            .formatted(P256DH, AUTH)))
            .hasMessageContaining("push_subscriptions_endpoint_https");
        assertThatThrownBy(() -> execute("""
            INSERT INTO push_subscriptions (member_id, endpoint, p256dh, auth, created_at)
            SELECT id, 'https://push.example.com/a', '%s', 'short', now() FROM household_members WHERE name = 'Anna'"""
            .formatted(P256DH)))
            .hasMessageContaining("push_subscriptions_keys_base64url");

        assertThat(count()).isZero();
    }

    private void member(final String name, final String phone) {
        webTestClient.post()
            .uri("/household/member")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"name\": \"" + name + "\", \"phone\": \"" + phone + "\", \"active\": true}")
            .exchange()
            .expectStatus().isCreated();
    }

    private static String subscription(final String endpoint, final String p256dh, final String userAgent) {
        return "{\"endpoint\": \"" + endpoint + "\", \"keys\": {\"p256dh\": \"" + p256dh + "\", \"auth\": \"" + AUTH + "\"}"
            + (userAgent == null ? "" : ", \"userAgent\": \"" + userAgent + "\"") + "}";
    }

    private WebTestClient.ResponseSpec stored(final String member, final String body) {
        return webTestClient.put()
            .uri("/household/member/{name}/push-subscriptions", member)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .exchange();
    }

    private void removed(final String member, final String endpoint) {
        webTestClient.delete()
            .uri(uri -> uri.path("/household/member/{name}/push-subscriptions").queryParam("endpoint", endpoint)
                .build(member))
            .exchange()
            .expectStatus().isNoContent();
    }

    private List<String> endpointsOf(final String members) {
        final List<Map<String, Object>> subscriptions = webTestClient.get()
            .uri("/push-subscriptions?members={members}", members)
            .exchange()
            .expectStatus().isOk()
            .expectBodyList(Map.class)
            .returnResult()
            .getResponseBody()
            .stream()
            .map(subscription -> (Map<String, Object>) subscription)
            .toList();
        return subscriptions.stream().map(subscription -> (String) subscription.get("endpoint")).toList();
    }

    private Map<String, Object> subscriptionOf(final String endpoint) {
        return row("""
            SELECT ps.*, m.name AS member FROM push_subscriptions ps JOIN household_members m ON m.id = ps.member_id
            WHERE ps.endpoint = '%s'""".formatted(endpoint));
    }

    private long count() {
        return (Long) row("SELECT count(*) AS subscriptions FROM push_subscriptions").get("subscriptions");
    }
}
