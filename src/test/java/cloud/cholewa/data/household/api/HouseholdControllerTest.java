package cloud.cholewa.data.household.api;

import cloud.cholewa.data.config.ExceptionHandlerConfig;
import cloud.cholewa.data.error.HouseholdException;
import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.HouseholdNotFoundException;
import cloud.cholewa.data.household.service.HouseholdMemberService;
import cloud.cholewa.home.model.HouseholdMember;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Import(ExceptionHandlerConfig.class)
@WebFluxTest(controllers = HouseholdController.class)
class HouseholdControllerTest {

    private static final HouseholdMember MEMBER = HouseholdMember.builder()
        .name("Ola")
        .phone("111-222-333")
        .active(true)
        .build();

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private HouseholdMemberService householdMemberService;

    @Test
    void should_return_household_members() {
        when(householdMemberService.getAllHouseholdMembers()).thenReturn(Mono.just(List.of(MEMBER)));

        webTestClient.get()
            .uri("/household")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$[0].name").isEqualTo("Ola")
            .jsonPath("$[0].phone").isEqualTo("111-222-333")
            .jsonPath("$[0].active").isEqualTo(true);
    }

    @Test
    void should_return_not_found_when_there_are_no_members() {
        when(householdMemberService.getAllHouseholdMembers())
            .thenReturn(Mono.error(new HouseholdNotFoundException("test")));

        webTestClient.get()
            .uri("/household")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.errors[0].message").isEqualTo("Cannot find any householder");
    }

    @Test
    void should_add_member_with_three_letter_name() {
        when(householdMemberService.addHouseholdMember(any())).thenReturn(Mono.just(MEMBER));

        webTestClient.post()
            .uri("/household/member")
            .body(BodyInserters.fromValue(Map.of("name", "Ola", "phone", "111-222-333")))
            .exchange()
            .expectStatus().isCreated()
            .expectBody()
            .jsonPath("$.name").isEqualTo("Ola");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidMembers")
    void should_reject_invalid_member(final String description, final Map<String, String> body) {
        webTestClient.post()
            .uri("/household/member")
            .body(BodyInserters.fromValue(body))
            .exchange()
            .expectStatus().isBadRequest();

        verifyNoInteractions(householdMemberService);
    }

    static Stream<Arguments> invalidMembers() {
        return Stream.of(
            Arguments.of("name shorter than 3", Map.of("name", "Al", "phone", "111-222-333")),
            Arguments.of("name longer than 50", Map.of("name", "a".repeat(51), "phone", "111-222-333")),
            Arguments.of("missing name", Map.of("phone", "111-222-333")),
            Arguments.of("phone in a wrong format", Map.of("name", "Ola", "phone", "111222333")),
            Arguments.of("missing phone", Map.of("name", "Ola"))
        );
    }

    @Test
    void should_return_conflict_when_member_clashes_with_existing_one() {
        when(householdMemberService.addHouseholdMember(any())).thenReturn(Mono.error(
            new HouseholdException("Phone number [111-222-333] is already assigned to another household member")));

        webTestClient.post()
            .uri("/household/member")
            .body(BodyInserters.fromValue(MEMBER))
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.CONFLICT)
            .expectBody()
            .jsonPath("$.errors[0].message").isEqualTo("Household member already exists")
            .jsonPath("$.errors[0].details")
            .isEqualTo("Phone number [111-222-333] is already assigned to another household member");
    }

    @Test
    void should_remove_member() {
        when(householdMemberService.removeHouseholdMember("Ola")).thenReturn(Mono.empty());

        webTestClient.delete()
            .uri("/household/member/Ola")
            .exchange()
            .expectStatus().isNoContent();

        verify(householdMemberService).removeHouseholdMember("Ola");
    }

    @Test
    void should_return_not_found_when_removing_unknown_member() {
        when(householdMemberService.removeHouseholdMember("Nobody"))
            .thenReturn(Mono.error(new HouseholdMemberNotFoundException("No household member named [Nobody]")));

        webTestClient.delete()
            .uri("/household/member/Nobody")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.errors[0].message").isEqualTo("Household member not found")
            .jsonPath("$.errors[0].details").isEqualTo("No household member named [Nobody]");
    }

    @Test
    void should_update_member() {
        when(householdMemberService.updateHouseholdMember(eq("Ola"), any())).thenReturn(Mono.just(MEMBER));

        webTestClient.patch()
            .uri("/household/member/Ola")
            .body(BodyInserters.fromValue(MEMBER))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("Ola");
    }

    @Test
    void should_reject_invalid_update() {
        webTestClient.patch()
            .uri("/household/member/Ola")
            .body(BodyInserters.fromValue(Map.of("name", "Al", "phone", "111-222-333")))
            .exchange()
            .expectStatus().isBadRequest();

        verifyNoInteractions(householdMemberService);
    }

    @Test
    void should_activate_member() {
        when(householdMemberService.activateHouseholdMember("Ola")).thenReturn(Mono.just(MEMBER));

        webTestClient.post()
            .uri("/household/member/Ola/activate")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.active").isEqualTo(true);
    }

    @Test
    void should_deactivate_member() {
        when(householdMemberService.deactivateHouseholdMember("Ola"))
            .thenReturn(Mono.just(HouseholdMember.builder().name("Ola").phone("111-222-333").active(false).build()));

        webTestClient.post()
            .uri("/household/member/Ola/deactivate")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.active").isEqualTo(false);
    }

    @Test
    void should_return_not_found_when_deactivating_unknown_member() {
        when(householdMemberService.deactivateHouseholdMember("Nobody"))
            .thenReturn(Mono.error(new HouseholdMemberNotFoundException("No household member named [Nobody]")));

        webTestClient.post()
            .uri("/household/member/Nobody/deactivate")
            .exchange()
            .expectStatus().isNotFound();
    }

    @Test
    void should_reject_device_with_invalid_mac() {
        webTestClient.post()
            .uri("/household/member/Ola/device")
            .body(BodyInserters.fromValue(Map.of("name", "iPhone", "mac", "AA-BB-CC-DD-EE-FF")))
            .exchange()
            .expectStatus().isBadRequest();
    }
}
