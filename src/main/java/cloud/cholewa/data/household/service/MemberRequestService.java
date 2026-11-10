package cloud.cholewa.data.household.service;

import cloud.cholewa.data.error.HouseholdMemberNotFoundException;
import cloud.cholewa.data.error.InvalidMemberRequestException;
import cloud.cholewa.data.error.MemberRequestNotFoundException;
import cloud.cholewa.data.error.MemberSwitchedOffException;
import cloud.cholewa.data.household.mapper.MemberRequestMapper;
import cloud.cholewa.data.household.model.HouseholdMemberEntity;
import cloud.cholewa.data.household.model.MemberRequestEntity;
import cloud.cholewa.data.household.repository.HouseholdMemberRepository;
import cloud.cholewa.data.household.repository.MemberRequestRepository;
import cloud.cholewa.home.model.MemberRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

//The household requests (HAS-409, epic HAS-407): an errand one member writes for another, texted to its
//recipient once by notification-service at 08:00 and kept until somebody ticks it off. Owner's decisions
//of 2026-11-05: anyone switched on may write to anyone switched on, and anyone may tick a request off or
//remove it; done requests are kept DONE_KEPT and deleted by the next write to the list (no scheduler here).
//A request is logged by its member, id and author - never its text, which is the household's
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberRequestService {

    static final Duration DONE_KEPT = Duration.ofDays(30);

    private final HouseholdMemberRepository householdMemberRepository;
    private final MemberRequestRepository memberRequestRepository;
    private final MemberRequestMapper memberRequestMapper;

    //the open requests of the member, oldest first; with done also those done within DONE_KEPT
    public Mono<List<MemberRequest>> list(final String memberName, final boolean done) {
        return findMember(memberName)
            .flatMap(member -> Mono.defer(() -> memberRequestRepository
                    .findListOfMember(member.getId(), done ? now().minus(DONE_KEPT) : null)
                    .collectList())
                .map(rows -> rows.stream().map(row -> memberRequestMapper.toMemberRequest(row, member.getName())).toList()));
    }

    //Only the text and the author of the body are read; the author must be a member switched on, stored as
    //the registry spells the name. The recipient must be switched on too. The done requests of the list
    //older than DONE_KEPT go first, in the same chain
    public Mono<MemberRequest> create(final String memberName, final MemberRequest body) {
        return Mono.fromCallable(() -> new Checked(MemberRequestRules.text(body.getText()),
                MemberRequestRules.name(body.getAuthor(), "author")))
            .flatMap(checked -> activeRecipient(memberName)
                .flatMap(member -> activeMember(checked.author(), "author")
                    .flatMap(author -> Mono.defer(() -> {
                        final LocalDateTime now = now();
                        return memberRequestRepository.deleteDoneBefore(member.getId(), now.minus(DONE_KEPT))
                            .then(memberRequestRepository.save(MemberRequestEntity.builder()
                                .memberId(member.getId())
                                .text(checked.text())
                                .author(author.getName())
                                .createdAt(now)
                                .build()));
                    }))
                    .doOnNext(stored -> log.info("Stored request {} of household member [{}] by [{}]", stored.getId(),
                        member.getName(), stored.getAuthor()))
                    .map(stored -> memberRequestMapper.toMemberRequest(stored, member.getName()))));
    }

    //Done unless done already: the first doneAt and doneBy stay, so ticking twice is no error. who must be
    //a member switched on, stored as the registry spells the name
    public Mono<MemberRequest> done(final String memberName, final Long id, final String who) {
        return Mono.fromCallable(() -> MemberRequestRules.name(who, "member who ticked it off"))
            .flatMap(checked -> findMember(memberName)
                .flatMap(member -> activeMember(checked, "member who ticked it off")
                    .flatMap(doneBy -> Mono.defer(() -> memberRequestRepository
                            .markDone(member.getId(), id, now(), doneBy.getName()))
                        .doOnNext(changed -> log.info("Request {} of household member [{}] ticked off by [{}]: {} row(s)",
                            id, member.getName(), doneBy.getName(), changed)))
                    .then(Mono.defer(() -> memberRequestRepository.findOneOfMember(member.getId(), id)))
                    .switchIfEmpty(Mono.error(() -> MemberRequestNotFoundException.forId(memberName, id)))
                    .map(row -> memberRequestMapper.toMemberRequest(row, member.getName()))));
    }

    public Mono<Void> remove(final String memberName, final Long id) {
        return findMember(memberName)
            .flatMap(member -> memberRequestRepository.deleteOfMember(member.getId(), id)
                .filter(removed -> removed > 0)
                .switchIfEmpty(Mono.error(() -> MemberRequestNotFoundException.forId(memberName, id)))
                .doOnNext(removed -> log.info("Removed request {} of household member [{}]", id, member.getName())))
            .then();
    }

    //The read of notification-service: every open request not yet texted of every member switched on, by
    //member and oldest first
    public Mono<List<MemberRequest>> pending() {
        return answered(memberRequestRepository.findPending());
    }

    //HAS-415: the requests ticked off whose authors were not told yet, the oldest tick first - notification-
    //service tells each author and marks them done-announced
    public Mono<List<MemberRequest>> doneToAnnounce() {
        return answered(memberRequestRepository.findDoneToAnnounce());
    }

    //HAS-415: the open texted requests of every member switched on, by member - the evening reminder. With
    //remindedBefore only those not reminded since then, so a repeat of the same evening reminds nobody twice
    public Mono<List<MemberRequest>> toRemind(final String remindedBefore) {
        return Mono.fromCallable(() -> Optional.ofNullable(MemberRequestRules.time(remindedBefore, "remindedBefore", false)))
            .flatMap(before -> answered(memberRequestRepository.findToRemind(before.orElse(null))));
    }

    //HAS-415: the open requests texted before sentBefore whose authors were never told they are still open
    public Mono<List<MemberRequest>> stale(final String sentBefore) {
        return Mono.fromCallable(() -> MemberRequestRules.time(sentBefore, "sentBefore", true))
            .flatMap(before -> answered(memberRequestRepository.findStale(before)));
    }

    //The requests notification-service texted: sentAt now on those not sent yet - an id sent before or
    //unknown is skipped, so a repeat after a cut-off changes nothing. The number of requests marked
    public Mono<Long> markSent(final List<Long> ids) {
        return marked(ids, "sent", memberRequestRepository::markSent);
    }

    //HAS-415: the marks of what notification-service sent, each where it may stand and the announcements once
    public Mono<Long> markReminded(final List<Long> ids) {
        return marked(ids, "reminded", memberRequestRepository::markReminded);
    }

    public Mono<Long> markDoneAnnounced(final List<Long> ids) {
        return marked(ids, "announced done", memberRequestRepository::markDoneAnnounced);
    }

    public Mono<Long> markStaleAnnounced(final List<Long> ids) {
        return marked(ids, "announced still open", memberRequestRepository::markStaleAnnounced);
    }

    private Mono<List<MemberRequest>> answered(final Flux<MemberRequestRepository.PendingRequest> rows) {
        return rows
            .map(row -> memberRequestMapper.toMemberRequest(row.request(), row.memberName()))
            .collectList();
    }

    private Mono<Long> marked(final List<Long> ids, final String what,
                              final BiFunction<List<Long>, LocalDateTime, Mono<Long>> mark) {
        return Mono.justOrEmpty(ids)
            .filter(list -> !list.isEmpty() && list.stream().noneMatch(Objects::isNull))
            .switchIfEmpty(Mono.error(() -> new InvalidMemberRequestException(
                "The ids of the requests " + what + " are required")))
            .flatMap(list -> Mono.defer(() -> mark.apply(list, now())))
            .doOnNext(marked -> log.info("Marked {} of {} household request(s) as {}", marked, ids.size(), what));
    }

    private Mono<HouseholdMemberEntity> findMember(final String name) {
        return householdMemberRepository.findByNameIgnoreCase(name)
            .switchIfEmpty(Mono.error(() -> HouseholdMemberNotFoundException.forName(name)));
    }

    private Mono<HouseholdMemberEntity> activeRecipient(final String name) {
        return findMember(name)
            .filter(HouseholdMemberEntity::isActive)
            .switchIfEmpty(Mono.error(() -> new MemberSwitchedOffException(
                "Household member [" + name + "] is switched off and takes no request")));
    }

    //the author, or who ticked it off: a name of the registry, of a member switched on - a refusal of the
    //request (INVALID_REQUEST), not a 404 of the address
    private Mono<HouseholdMemberEntity> activeMember(final String name, final String what) {
        return householdMemberRepository.findByNameIgnoreCase(name)
            .filter(HouseholdMemberEntity::isActive)
            .switchIfEmpty(Mono.error(() -> new InvalidMemberRequestException(
                "The " + what + " [" + name + "] is no household member switched on")));
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault()).truncatedTo(ChronoUnit.MICROS);
    }

    private record Checked(String text, String author) {
    }
}
