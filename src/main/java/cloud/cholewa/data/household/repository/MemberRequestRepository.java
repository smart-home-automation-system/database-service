package cloud.cholewa.data.household.repository;

import cloud.cholewa.data.household.model.MemberRequestEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collection;

//The household requests (V21, the marks of V23). Not an R2dbcRepository: the writes are conditional ("done
//unless done already", "sent unless sent already", every mark of HAS-415 likewise) and the reads of
//notification-service join the registry - each one statement, so a second caller cannot get between a read
//and a write
@Repository
@RequiredArgsConstructor
public class MemberRequestRepository {

    //the reads of notification-service: a request with the name of its member as the registry spells it now
    private static final String WITH_MEMBER_NAME = """
        SELECT r.*, m.name AS member_name
        FROM member_requests r
            JOIN household_members m ON m.id = r.member_id
        WHERE\s""";
    private static final String BY_MEMBER = " ORDER BY upper(m.name), r.created_at, r.id";

    //every open request not yet texted of every member switched on - by member, then oldest first
    private static final String PENDING = WITH_MEMBER_NAME
        + "m.active AND r.sent_at IS NULL AND r.done_at IS NULL" + BY_MEMBER;

    //HAS-415: ticked off and its author not told yet - whoever its member is now, switched off included
    private static final String DONE_TO_ANNOUNCE = WITH_MEMBER_NAME
        + "r.done_at IS NOT NULL AND r.done_announced_at IS NULL ORDER BY r.done_at, r.id";

    //HAS-415: open, texted, of a member switched on, and not reminded since the time given (all when none is)
    private static final String TO_REMIND = WITH_MEMBER_NAME
        + "m.active AND r.done_at IS NULL AND r.sent_at IS NOT NULL"
        + " AND (CAST(:remindedBefore AS TIMESTAMP) IS NULL OR r.reminded_at IS NULL"
        + " OR r.reminded_at < CAST(:remindedBefore AS TIMESTAMP))" + BY_MEMBER;

    //HAS-415: open, texted before the time given, of a member switched on, its author never told it is open
    private static final String STALE = WITH_MEMBER_NAME
        + "m.active AND r.done_at IS NULL AND r.stale_announced_at IS NULL AND r.sent_at < :sentBefore" + BY_MEMBER;

    private static final String MARK_SENT = """
        UPDATE member_requests SET sent_at = :at
        WHERE id = ANY(CAST(:ids AS BIGINT[])) AND sent_at IS NULL""";

    //the last reminder: of an open texted request only, moved on by every evening
    private static final String MARK_REMINDED = """
        UPDATE member_requests SET reminded_at = :at
        WHERE id = ANY(CAST(:ids AS BIGINT[])) AND done_at IS NULL AND sent_at IS NOT NULL""";

    private static final String MARK_DONE_ANNOUNCED = """
        UPDATE member_requests SET done_announced_at = :at
        WHERE id = ANY(CAST(:ids AS BIGINT[])) AND done_at IS NOT NULL AND done_announced_at IS NULL""";

    private static final String MARK_STALE_ANNOUNCED = """
        UPDATE member_requests SET stale_announced_at = :at
        WHERE id = ANY(CAST(:ids AS BIGINT[])) AND sent_at IS NOT NULL AND stale_announced_at IS NULL""";

    //done unless done already; ticked off by its own author it counts as announced at once (HAS-415) - the
    //names compared as the registry keeps them unique (V10), with upper() on both sides
    private static final String MARK_DONE = """
        UPDATE member_requests
        SET done_at = :doneAt, done_by = :doneBy,
            done_announced_at = CASE WHEN upper(author) = upper(:doneBy) THEN CAST(:doneAt AS TIMESTAMP) END
        WHERE id = :id AND member_id = :memberId AND done_at IS NULL""";

    private static final String MEMBER_ID = "member_id";
    private static final String DONE_AT = "done_at";

    private final R2dbcEntityTemplate template;

    public Mono<MemberRequestEntity> save(final MemberRequestEntity request) {
        return template.insert(request);
    }

    //the list of a member: the open requests, and with doneSince also those done since then - oldest first
    public Flux<MemberRequestEntity> findListOfMember(final Long memberId, final LocalDateTime doneSince) {
        final Criteria open = Criteria.where(DONE_AT).isNull();
        final Criteria shown = doneSince == null ? open : open.or(DONE_AT).greaterThanOrEquals(doneSince);
        return template.select(
            Query.query(Criteria.where(MEMBER_ID).is(memberId).and(shown))
                .sort(Sort.by("created_at", "id")),
            MemberRequestEntity.class);
    }

    public Mono<MemberRequestEntity> findOneOfMember(final Long memberId, final Long id) {
        return template.selectOne(
            Query.query(Criteria.where("id").is(id).and(MEMBER_ID).is(memberId)), MemberRequestEntity.class);
    }

    //done unless done already - the first doneAt and doneBy stay; the number of rows changed
    public Mono<Long> markDone(final Long memberId, final Long id, final LocalDateTime doneAt, final String doneBy) {
        return template.getDatabaseClient().sql(MARK_DONE)
            .bind("doneAt", doneAt)
            .bind("doneBy", doneBy)
            .bind("id", id)
            .bind("memberId", memberId)
            .fetch()
            .rowsUpdated();
    }

    public Mono<Long> deleteOfMember(final Long memberId, final Long id) {
        return template.delete(
            Query.query(Criteria.where("id").is(id).and(MEMBER_ID).is(memberId)), MemberRequestEntity.class);
    }

    //the retention without a scheduler: the done requests of a member done before the time - never one whose
    //author was not told yet (HAS-415), so a long silence of notification-service loses no announcement
    public Mono<Long> deleteDoneBefore(final Long memberId, final LocalDateTime before) {
        return template.delete(
            Query.query(Criteria.where(MEMBER_ID).is(memberId).and(DONE_AT).lessThan(before)
                .and("done_announced_at").isNotNull()),
            MemberRequestEntity.class);
    }

    public Flux<PendingRequest> findPending() {
        return withMemberName(template.getDatabaseClient().sql(PENDING));
    }

    public Flux<PendingRequest> findDoneToAnnounce() {
        return withMemberName(template.getDatabaseClient().sql(DONE_TO_ANNOUNCE));
    }

    //remindedBefore null: every open texted request, reminded or not
    public Flux<PendingRequest> findToRemind(final LocalDateTime remindedBefore) {
        final DatabaseClient.GenericExecuteSpec sql = template.getDatabaseClient().sql(TO_REMIND);
        return withMemberName(remindedBefore == null
            ? sql.bindNull("remindedBefore", LocalDateTime.class)
            : sql.bind("remindedBefore", remindedBefore));
    }

    public Flux<PendingRequest> findStale(final LocalDateTime sentBefore) {
        return withMemberName(template.getDatabaseClient().sql(STALE).bind("sentBefore", sentBefore));
    }

    //sent unless sent already: an id texted before, or unknown, is skipped. The number of rows changed
    public Mono<Long> markSent(final Collection<Long> ids, final LocalDateTime sentAt) {
        return mark(MARK_SENT, ids, sentAt);
    }

    //HAS-415: each mark only where it may stand (the check of V23), the announcements once - an id that does
    //not fit, or is unknown, is skipped. The number of rows changed
    public Mono<Long> markReminded(final Collection<Long> ids, final LocalDateTime at) {
        return mark(MARK_REMINDED, ids, at);
    }

    public Mono<Long> markDoneAnnounced(final Collection<Long> ids, final LocalDateTime at) {
        return mark(MARK_DONE_ANNOUNCED, ids, at);
    }

    public Mono<Long> markStaleAnnounced(final Collection<Long> ids, final LocalDateTime at) {
        return mark(MARK_STALE_ANNOUNCED, ids, at);
    }

    private Mono<Long> mark(final String statement, final Collection<Long> ids, final LocalDateTime at) {
        return template.getDatabaseClient().sql(statement)
            .bind("ids", ids.toArray(Long[]::new))
            .bind("at", at)
            .fetch()
            .rowsUpdated();
    }

    private Flux<PendingRequest> withMemberName(final DatabaseClient.GenericExecuteSpec sql) {
        return sql
            .map((row, metadata) -> new PendingRequest(
                template.getConverter().read(MemberRequestEntity.class, row, metadata),
                row.get("member_name", String.class)))
            .all();
    }

    //a request with the name of its member as the registry spells it now - the reads of notification-service
    public record PendingRequest(MemberRequestEntity request, String memberName) {
    }
}
