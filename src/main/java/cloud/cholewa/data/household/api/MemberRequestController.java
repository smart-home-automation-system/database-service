package cloud.cholewa.data.household.api;

import cloud.cholewa.data.error.InvalidMemberRequestException;
import cloud.cholewa.data.household.service.MemberRequestService;
import cloud.cholewa.home.model.MemberRequest;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

//The household requests (HAS-409, epic HAS-407), on endpoints of their own below the routed /household -
//never a field of HouseholdMember, so a whole write of a member cannot drop them. The tails of the route
//are open: every endpoint here is reachable from outside the cluster the day it is deployed, the read of
//notification-service included (the texts of the household, LAN / VPN only - the rule of GET /household).
//The bodies are not @Valid: MemberRequestRules answers with INVALID_REQUEST and the reason instead
@RestController
@RequiredArgsConstructor
public class MemberRequestController {

    private final MemberRequestService memberRequestService;

    //the open requests of the member, oldest first; done=true adds those done in the last 30 days
    @GetMapping("/household/member/{name}/requests")
    Mono<ResponseEntity<List<MemberRequest>>> getRequests(
        @PathVariable final String name,
        @RequestParam(defaultValue = "false") final boolean done
    ) {
        return memberRequestService.list(name, done).map(ResponseEntity::ok);
    }

    //the text and the author of the body are read, the rest is the server's
    @PostMapping("/household/member/{name}/requests")
    Mono<ResponseEntity<MemberRequest>> addRequest(
        @PathVariable final String name,
        @RequestBody final MemberRequest request
    ) {
        return memberRequestService.create(name, request)
            .map(stored -> ResponseEntity.status(HttpStatus.CREATED).body(stored));
    }

    //ticked off by the member given; done twice keeps the first time
    @PutMapping("/household/member/{name}/requests/{id}/done")
    Mono<ResponseEntity<MemberRequest>> markDone(
        @PathVariable final String name,
        @PathVariable final Long id,
        @RequestParam final String member
    ) {
        return memberRequestService.done(name, id, member).map(ResponseEntity::ok);
    }

    @DeleteMapping("/household/member/{name}/requests/{id}")
    Mono<ResponseEntity<Void>> removeRequest(@PathVariable final String name, @PathVariable final Long id) {
        return memberRequestService.remove(name, id).then(Mono.just(ResponseEntity.noContent().build()));
    }

    //the read of notification-service at 08:00: every open request not yet texted of every member switched
    //on. Only pending=true is served - the parameter says what the list is, for a reader of the address
    @GetMapping("/household/requests")
    Mono<ResponseEntity<List<MemberRequest>>> getPendingRequests(@RequestParam final boolean pending) {
        return Mono.just(pending)
            .filter(Boolean::booleanValue)
            .switchIfEmpty(Mono.error(() -> new InvalidMemberRequestException("Only pending=true is served")))
            .flatMap(asked -> memberRequestService.pending())
            .map(ResponseEntity::ok);
    }

    //the requests notification-service texted: the number marked (an id sent before or unknown is skipped)
    @PutMapping("/household/requests/sent")
    Mono<ResponseEntity<Long>> markSent(@RequestBody @Size(max = 500) final List<Long> ids) {
        return memberRequestService.markSent(ids).map(ResponseEntity::ok);
    }

    //HAS-415, the reads of notification-service: the requests ticked off whose authors were not told yet
    @GetMapping("/household/requests/done-to-announce")
    Mono<ResponseEntity<List<MemberRequest>>> getDoneToAnnounce() {
        return memberRequestService.doneToAnnounce().map(ResponseEntity::ok);
    }

    //the open texted requests of the members switched on - not reminded since remindedBefore, when given
    @GetMapping("/household/requests/to-remind")
    Mono<ResponseEntity<List<MemberRequest>>> getToRemind(@RequestParam(required = false) final String remindedBefore) {
        return memberRequestService.toRemind(remindedBefore).map(ResponseEntity::ok);
    }

    //the open requests texted before sentBefore whose authors were never told they are still open
    @GetMapping("/household/requests/stale")
    Mono<ResponseEntity<List<MemberRequest>>> getStale(@RequestParam(required = false) final String sentBefore) {
        return memberRequestService.stale(sentBefore).map(ResponseEntity::ok);
    }

    //HAS-415, the marks of what notification-service sent: the number marked (an id that does not fit is skipped)
    @PutMapping("/household/requests/reminded")
    Mono<ResponseEntity<Long>> markReminded(@RequestBody @Size(max = 500) final List<Long> ids) {
        return memberRequestService.markReminded(ids).map(ResponseEntity::ok);
    }

    @PutMapping("/household/requests/done-announced")
    Mono<ResponseEntity<Long>> markDoneAnnounced(@RequestBody @Size(max = 500) final List<Long> ids) {
        return memberRequestService.markDoneAnnounced(ids).map(ResponseEntity::ok);
    }

    @PutMapping("/household/requests/stale-announced")
    Mono<ResponseEntity<Long>> markStaleAnnounced(@RequestBody @Size(max = 500) final List<Long> ids) {
        return memberRequestService.markStaleAnnounced(ids).map(ResponseEntity::ok);
    }
}
