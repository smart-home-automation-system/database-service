package cloud.cholewa.data.household.api;

import cloud.cholewa.data.household.config.HouseholdPushProperties;
import cloud.cholewa.data.household.service.PushSubscriptionService;
import cloud.cholewa.home.model.PushSubscription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

//The Web Push subscriptions of the members' browsers (HAS-272), on endpoints of their own: never a
//field of HouseholdMember, so a whole write of a member cannot drop them and no read of the
//household hands their keys out.
//
//Two audiences, two places. The writes and the public key are the installed application's and sit
//below /household, which the gateway routes. The read with the keys is notification-service's and
//sits below /push-subscriptions, which the gateway does not route: inside the cluster only. Never
//move it below a routed prefix - the tails of the database route are open (see CLAUDE.md)
@RestController
@RequiredArgsConstructor
public class PushSubscriptionController {

    private final PushSubscriptionService pushSubscriptionService;
    private final HouseholdPushProperties pushProperties;

    @GetMapping("/household/push-config")
    Mono<ResponseEntity<PushConfig>> getPushConfig() {
        return Mono.just(ResponseEntity.ok(new PushConfig(pushProperties.publicKey())));
    }

    //upsert by the endpoint: the stored subscription is the answer
    @PutMapping("/household/member/{name}/push-subscriptions")
    Mono<ResponseEntity<PushSubscription>> storePushSubscription(
        @PathVariable final String name,
        @Valid @RequestBody final PushSubscription subscription
    ) {
        return pushSubscriptionService.store(name, subscription)
            .map(ResponseEntity::ok);
    }

    //204 also when the member had no such subscription - a browser may unsubscribe twice
    @DeleteMapping("/household/member/{name}/push-subscriptions")
    Mono<ResponseEntity<Void>> removePushSubscription(
        @PathVariable final String name,
        @RequestParam @NotBlank @Size(max = 2000) final String endpoint
    ) {
        return pushSubscriptionService.remove(name, endpoint)
            .then(Mono.just(ResponseEntity.noContent().build()));
    }

    //inside the cluster only (not routed): the subscriptions of the named members who are switched on,
    //keys included - what notification-service posts the household messages to
    @GetMapping("/push-subscriptions")
    Mono<ResponseEntity<List<PushSubscription>>> getPushSubscriptionsOfMembers(
        @RequestParam @Size(min = 1, max = 20) final List<@NotBlank @Size(max = 50) String> members
    ) {
        return pushSubscriptionService.ofMembers(members)
            .map(ResponseEntity::ok);
    }
}
