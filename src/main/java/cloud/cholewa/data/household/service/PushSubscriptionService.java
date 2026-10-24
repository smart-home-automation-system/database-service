package cloud.cholewa.data.household.service;

import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.MemberSwitchedOffException;
import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import cloud.cholewa.data.household.model.PushSubscriptionEntity;
import cloud.cholewa.data.household.repository.HouseholdMemberRepository;
import cloud.cholewa.data.household.repository.PushSubscriptionRepository;
import cloud.cholewa.home.model.PushSubscription;
import cloud.cholewa.home.model.PushSubscriptionKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

//The Web Push subscriptions of the members' browsers (HAS-272). Written by the installed application
//on endpoints of their own, read by notification-service for the members at home. A subscription is
//never logged whole: its keys are what a sender encrypts with for that browser - the host of the
//endpoint tells the push service, which is all a log line needs
@Slf4j
@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

    private final HouseholdMemberRepository householdMemberRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;

    //The endpoint is the subscription: a browser that subscribes again replaces what it said before,
    //and one that subscribes for another member (a shared tablet on another personal link) is from
    //then on that member's. A member switched off gets none - switching off clears them
    public Mono<PushSubscription> store(final String memberName, final PushSubscription subscription) {
        return findMember(memberName)
            .filter(HouseholdMemberEntity::isActive)
            .switchIfEmpty(Mono.error(() -> new MemberSwitchedOffException(
                "Household member [" + memberName + "] is switched off and takes no push subscription")))
            .map(member -> toEntity(member.getId(), subscription))
            .flatMap(pushSubscriptionRepository::store)
            .doOnNext(stored -> log.info("Stored the push subscription of household member [{}] on [{}]",
                memberName, hostOf(stored.getEndpoint())))
            .map(PushSubscriptionService::toModel);
    }

    //Removing a subscription that is not there is no error: a browser that unsubscribes twice, or one
    //cleared by switching its member off, gets the same answer. The member has to exist
    public Mono<Void> remove(final String memberName, final String endpoint) {
        return findMember(memberName)
            .flatMap(member -> pushSubscriptionRepository.deleteOfMember(member.getId(), endpoint))
            .doOnNext(removed -> log.info("Removed {} push subscription(s) of household member [{}]", removed, memberName))
            .then();
    }

    //The read of notification-service: the subscriptions of the named members who are switched on, a
    //name the registry does not know names nobody. In the order they were stored (the query's)
    public Mono<List<PushSubscription>> ofMembers(final List<String> memberNames) {
        return pushSubscriptionRepository.findAllOfActiveMembers(memberNames)
            .map(PushSubscriptionService::toModel)
            .collectList();
    }

    private Mono<HouseholdMemberEntity> findMember(final String name) {
        return householdMemberRepository.findByNameIgnoreCase(name)
            .switchIfEmpty(Mono.error(() -> HouseholdMemberNotFoundException.forName(name)));
    }

    //the registry gives the time it was stored, whatever the payload says of it
    private static PushSubscriptionEntity toEntity(final Long memberId, final PushSubscription subscription) {
        return PushSubscriptionEntity.builder()
            .memberId(memberId)
            .endpoint(subscription.getEndpoint())
            .p256dh(subscription.getKeys().getP256dh())
            .auth(subscription.getKeys().getAuth())
            .userAgent(subscription.getUserAgent())
            .createdAt(LocalDateTime.now(ZoneId.systemDefault()))
            .build();
    }

    private static PushSubscription toModel(final PushSubscriptionEntity entity) {
        return PushSubscription.builder()
            .endpoint(entity.getEndpoint())
            .keys(PushSubscriptionKeys.builder().p256dh(entity.getP256dh()).auth(entity.getAuth()).build())
            .userAgent(entity.getUserAgent())
            .createdAt(entity.getCreatedAt())
            .build();
    }

    private static String hostOf(final String endpoint) {
        try {
            return URI.create(endpoint).getHost();
        } catch (IllegalArgumentException e) {
            return "an endpoint that is no URI";
        }
    }
}
