package cloud.cholewa.data.calendar.repository;

import cloud.cholewa.data.calendar.model.DayOffEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;

//The table of the days off the household added. Not an R2dbcRepository: its save() decides between
//INSERT and UPDATE by whether the id is null, and here the id is the day a client names - a row
//with an id is always an UPDATE to it, which stores nothing for a day that is new.
@Repository
@RequiredArgsConstructor
public class DayOffRepository {

    //One statement for "set this day": a day that is stored gets the new reason and keeps the
    //time it was added. Asking first and writing second would be two statements a second caller
    //could get between
    private static final String SET_DAY_OFF = """
        INSERT INTO days_off (date, reason, created_at)
        VALUES (:date, :reason, :createdAt)
        ON CONFLICT (date) DO UPDATE SET reason = EXCLUDED.reason""";

    private final R2dbcEntityTemplate template;

    //the stored days of a range, the start included and the end not; in no particular order
    public Flux<DayOffEntity> findAllBetween(final LocalDate from, final LocalDate to) {
        return template.select(
            Query.query(Criteria.where("date").greaterThanOrEquals(from).and("date").lessThan(to)),
            DayOffEntity.class
        );
    }

    public Mono<Void> set(final LocalDate date, final String reason, final LocalDateTime createdAt) {
        return template.getDatabaseClient().sql(SET_DAY_OFF)
            .bind("date", date)
            .bind("reason", reason)
            .bind("createdAt", createdAt)
            .then();
    }

    //the number of rows that went: none when the day was not stored
    public Mono<Long> deleteByDate(final LocalDate date) {
        return template.delete(Query.query(Criteria.where("date").is(date)), DayOffEntity.class);
    }
}
